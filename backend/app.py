"""Hive backend: one WSGI app (Vercel's Python entrypoint). Routes:

POST /api/qr: a photo of a Serbian fiscal receipt whose QR code the phone couldn't read -> the receipt's link.
  Body: the image bytes (JPEG / PNG / WebP). Header: Authorization: Bearer <QR_KEY> (the key lives in Vercel's
  environment and in Hive's settings, never in the repo). Answer: {"link": "https://suf.purs.gov.rs/v/?vl=..."} or
  {"link": null}. A link is only returned when it passed every check of the repair reader; never a guess.
GET /api/qr: a liveness check.
"""
import hmac, json, os, re, tempfile, time

import cv2
import numpy as np

from qrfix import qr_geometry as G
from qrfix import qr_repair as R

LINK = re.compile(r'^https://suf\.purs\.gov\.rs/v/\?vl=[A-Za-z0-9%]+$')
BUDGET = int(os.environ.get('QR_BUDGET', 240))  # seconds of work; the function itself may run 300
MAX_BODY = 4_400_000  # Vercel's request body limit is 4.5 MB


def crops(path):
    """The QR code's region of a whole-receipt photo (the reader expects the code to fill most of the picture): OpenCV's
    detectors usually still find the code's corners when they can't decode it. The crop keeps a margin of a third of
    the code; the whole picture comes last."""
    img = cv2.imread(path, cv2.IMREAD_GRAYSCALE)
    if img is None:
        return [path]
    H, W = img.shape
    out = []
    for det in (cv2.QRCodeDetectorAruco(), cv2.QRCodeDetector()):
        try:
            ok, pts = det.detect(img)
        except Exception:
            ok, pts = False, None
        if not ok or pts is None:
            continue
        pts = np.asarray(pts).reshape(-1, 2)
        x0, y0 = pts.min(0); x1, y1 = pts.max(0)
        m = max(x1 - x0, y1 - y0) / 3
        x0, y0 = int(max(0, x0 - m)), int(max(0, y0 - m)); x1, y1 = int(min(W, x1 + m)), int(min(H, y1 + m))
        if (x1 - x0) * (y1 - y0) > 0.6 * W * H or x1 - x0 < 60:
            continue  # the code already fills the picture, or the find is a speck
        f = tempfile.NamedTemporaryFile(suffix='.png', delete=False)
        cv2.imwrite(f.name, img[y0:y1, x0:x1])
        out.append(f.name)
        break
    return out + [path]


def repair_any(path, budget=BUDGET):
    """[repair] on the code's crop first, then on the whole picture, within one time budget."""
    start = time.time()
    paths = crops(path)
    try:
        for p in paths:
            left = budget - (time.time() - start)
            if left <= 5:
                return None, 'timeout'
            link, how = repair(p, left)
            if link:
                return link, ('crop ' if p != path else '') + how
            if how == 'timeout':
                return None, how
        return None, 'not found'
    finally:
        for p in paths:
            if p != path:
                os.unlink(p)


def repair(path, budget=BUDGET):
    """The repair reader's attempts in turn (plain, then against the printer's white stripes, then upscaled for tiny
    codes) until one returns a checked link or the time is up."""
    start = time.time()
    for erode, scale in ((0, 1.0), (5, 1.0), (0, 2.0), (3, 2.0)):
        G.DEADLINE = start + budget
        try:
            t = R.solve2(path, erode=erode, scale=scale, log=lambda *a: None)
        except G.Timeout:
            return None, 'timeout'
        except Exception:  # one attempt failing (odd image) must not stop the others
            t = None
        if t and LINK.match(t):
            return t, f'erode={erode} scale={scale}'
    return None, 'not found'


def app(environ, start_response):
    def send(code, body):
        data = json.dumps(body).encode()
        start_response(code, [('Content-Type', 'application/json'), ('Content-Length', str(len(data)))])
        return [data]

    if environ.get('PATH_INFO', '').rstrip('/') != '/api/qr':
        return send('404 Not Found', {'error': 'not found'})
    if environ.get('REQUEST_METHOD') != 'POST':
        return send('200 OK', {'ok': True, 'post': 'an image with Authorization: Bearer <key>'})
    key = os.environ.get('QR_KEY', '')
    given = environ.get('HTTP_AUTHORIZATION', '').removeprefix('Bearer ').strip()
    if not key or not hmac.compare_digest(given.encode(), key.encode()):
        return send('401 Unauthorized', {'error': 'unauthorized'})
    n = int(environ.get('CONTENT_LENGTH') or 0)
    if n <= 0 or n > MAX_BODY:
        return send('413 Payload Too Large', {'error': 'send one image, at most 4.4 MB'})
    body = environ['wsgi.input'].read(n)
    start = time.time()
    with tempfile.NamedTemporaryFile(suffix='.img') as f:  # deleted right after: nothing is kept
        f.write(body)
        f.flush()
        link, how = repair_any(f.name)
    return send('200 OK', {'link': link, 'how': how, 'ms': int((time.time() - start) * 1000)})

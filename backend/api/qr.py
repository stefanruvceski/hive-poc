"""POST /api/qr: a photo of a Serbian fiscal receipt whose QR code the phone couldn't read -> the receipt's link.

Body: the image bytes (JPEG / PNG / WebP). Header: Authorization: Bearer <QR_KEY> (the key lives in Vercel's
environment and in Hive's settings, never in the repo). Answer: {"link": "https://suf.purs.gov.rs/v/?vl=..."} or
{"link": null}. A link is only returned when it passed every check of the repair reader; never a guess.
"""
import hmac, json, os, re, sys, tempfile, time
from http.server import BaseHTTPRequestHandler

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from qrfix import qr_geometry as G  # noqa: E402
from qrfix import qr_repair as R  # noqa: E402

LINK = re.compile(r'^https://suf\.purs\.gov\.rs/v/\?vl=[A-Za-z0-9%]+$')
BUDGET = int(os.environ.get('QR_BUDGET', 240))  # seconds of work; the function itself may run 300
MAX_BODY = 4_400_000  # Vercel's request body limit is 4.5 MB


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
        except Exception as e:  # one attempt failing (odd image) must not stop the others
            t = None
        if t and LINK.match(t):
            return t, f'erode={erode} scale={scale}'
    return None, 'not found'


class handler(BaseHTTPRequestHandler):
    def _send(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._send(200, {'ok': True, 'post': 'an image with Authorization: Bearer <key>'})

    def do_POST(self):
        key = os.environ.get('QR_KEY', '')
        given = self.headers.get('Authorization', '').removeprefix('Bearer ').strip()
        if not key or not hmac.compare_digest(given.encode(), key.encode()):
            return self._send(401, {'error': 'unauthorized'})
        n = int(self.headers.get('Content-Length') or 0)
        if n <= 0 or n > MAX_BODY:
            return self._send(413, {'error': 'send one image, at most 4.4 MB'})
        body = self.rfile.read(n)
        start = time.time()
        with tempfile.NamedTemporaryFile(suffix='.img') as f:  # deleted right after: nothing is kept
            f.write(body)
            f.flush()
            link, how = repair(f.name)
        self._send(200, {'link': link, 'how': how, 'ms': int((time.time() - start) * 1000)})

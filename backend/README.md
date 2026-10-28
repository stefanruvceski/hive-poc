# Hive backend (Vercel, Hobby plan)

Heavier work Hive's phone shouldn't do, as one Vercel Python function (`app.py`, a WSGI app). Deployed from this folder (Vercel project
root directory `backend`). No data is stored; secrets only in Vercel's environment variables.

- `POST /api/qr`: repair reader for receipt QR codes the phone can't read (crumpled paper, faded print, printer
  stripes, tiny modules). Body = image bytes, header `Authorization: Bearer $QR_KEY`. Answers
  `{"link": "https://suf.purs.gov.rs/v/?vl=…" | null, "how", "ms"}`. `qrfix/` is the reference reader
  (finder/alignment/timing → thin-plate spline → sampling at several thresholds → Reed-Solomon with erasures, the
  known prefix, header and padding filled in, every block checked: header 0100, 600 ≤ L, prefix, low nibbles 2..7).
  Stops after `BUDGET` seconds.

Test from a terminal:

    curl -s -X POST -H "Authorization: Bearer $QR_KEY" --data-binary @receipt.jpg https://<project>.vercel.app/api/qr

# Economofar API - upgrade notes

Installed by `economofar_upgrade.py`. Every file that was changed has a copy in `.upgrade-backup/`
(this folder). To go back: run `python3 economofar_upgrade.py --undo` from the Projects folder.

## Configuration (all optional except JWT_SECRET, which the installer already put in `.env`)

| Variable | Default | Meaning |
|---|---|---|
| `JWT_SECRET` | random at each start | Base64 secret (>= 32 bytes) used to sign tokens. **Keep it private.** |
| `JWT_EXPIRATION_HOURS` | 168 | Token lifetime (7 days). |
| `OLLAMA_URL` | existing value | Ollama endpoint. |
| `OLLAMA_MODEL` | `minicpm-v` | Vision model used for OCR. |
| `OLLAMA_TIMEOUT_SECONDS` | 180 | Max wait for one scan. |
| `TICKETS_ABANDONED_SCAN_HOURS` | 24 | Unfinished scans older than this are purged every hour. |

Generate another secret any time with: `openssl rand -base64 48`

## What changed

- **Security**: JWT secret no longer in the source code; invalid or expired tokens give a clean `401` JSON
  instead of a crash; login throttling (5 failures / 15 min -> `429`); stronger registration checks;
  `User` objects (password hash) are never serialized in JSON responses.
- **Scan flow**: `POST /api/v1/tickets/upload` now stores the photo and returns `ticketId` + `imageUrl`;
  `POST /api/v1/tickets/scan` finalizes that same ticket instead of creating a duplicate.
  OCR failures now return `502` with a readable message.
- **History / budget / dashboard / notifications** only count saved tickets (not unfinished scans).
  The "budget exceeded" notification is sent once per month.
- **New endpoints**
  - `GET  /api/analytics/summary?months=6`  (totals, per month, per category, top merchants, payment methods)
  - `POST /api/account/delete`  body `{ "password": "..." }`  (deletes the account and all its data)
- **Housekeeping**: tickets saved before this upgrade are repaired at startup; abandoned scans and their photos
  are purged automatically; deleting a receipt also deletes its photo (unless another receipt uses it).
- Upload limit raised to 15 MB (`src/main/resources/application.properties`).

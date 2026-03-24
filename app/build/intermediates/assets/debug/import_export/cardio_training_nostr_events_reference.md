# Cardio Training Nostr Events Reference

**Read this only if you care about Nostr relays.** It does **not** define the import file format.

For **turning user history into something ERV can import**, use **Cardio Training Import AI Guide** and **Cardio Training Import CSV Guide** in **Settings → Import And Export**. Those documents describe the **same JSON and CSV shapes** the app stores locally (`CardioDayLog`, `CardioSession`, etc.). This page explains how that data is **labeled and encrypted on relays** when the user has sync enabled.

---

## Event kind

ERV publishes cardio silo data as Nostr **kind `30078`** (application-specific replaceable events), **encrypted** so relay operators see ciphertext, not plaintext. **Weight training** uses the same kind for its silo; only the **`d` tag** prefix and the decrypted JSON payload type differ.

---

## Encryption

The event **`content`** field is **ciphertext** (self-encrypted to the user’s key). Recovering plaintext requires the user’s normal app decryption path.

---

## `d` tags (replaceable identifiers)

| `d` tag | Role |
| --- | --- |
| `erv/cardio/routines` | **Master** document: routines, custom activity types, and quick launches (`CardioMasterPayload`). |
| `erv/cardio/YYYY-MM-DD` | **One daily log** for that calendar date: `CardioDayLog` JSON. Multiple sessions on the same day live in the **`sessions` array** inside that single document. |

There is **no** separate per-session `d` tag for day logs in this design—session identity is inside the JSON.

---

## Import and relay upload

After a **successful file import**, the app updates **local storage** first, then may enqueue **Nostr publishes** (master + each affected day) in **`RelayPublishOutbox`**, with retries if relays fail—same reliability idea as weight training import.

---

## See also (in-app, same settings screen)

- **Cardio Training Import AI Guide** — JSON contract and Strava → ERV hints  
- **Cardio Training Import CSV Guide** — spreadsheet columns  

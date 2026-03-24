# Cardio Training Nostr Events Reference

This document describes how **cardio** data is represented on **Nostr** in Energy Radiance Vitality (ERV). It mirrors the contract the Android app implements in `CardioSync` and the **relay publish outbox** after import.

---

## Event Kind

ERV publishes cardio silo data as Nostr **kind `30078`** — the same **application-specific replaceable** pattern used for other encrypted ERV categories in this app (see weight training and `PLAN_OF_ACTION.md`).

---

## Encryption

The event **`content`** field holds a **ciphertext** produced by encrypting the JSON payload **to the user’s own key** (self-encryption). Relays store opaque blobs; readers need the user’s decryption path to recover plaintext.

---

## D-Tags (Replaceable Identifiers)

| `d` tag | Role |
| --- | --- |
| `erv/cardio/routines` | **Master** document: routines, custom activity types, and quick launches. Serialized as `CardioMasterPayload` in the app. |
| `erv/cardio/YYYY-MM-DD` | **Daily log** for that calendar date: `CardioDayLog` JSON. **Multiple sessions on the same day** are stored **inside one event** for that date (in the `sessions` array). |

There is **no separate per-session UUID d-tag** for routine day logs in the shipped design—session identity is inside the JSON.

---

## Field Names

Encrypted JSON field names match the Kotlin models shipped in the app (`CardioDayLog`, `CardioSession`, etc.). For **import** file formats, see **Cardio Training Import AI Guide** and **Cardio Training Import CSV Guide**.

---

## Import And Relay Upload

After a **successful file import**, the app updates **local storage first**, then enqueues **Nostr publishes** (master + each affected day) in **`RelayPublishOutbox`**, with retries and backoff if relays fail. This is the same reliability pattern as **weight training import**.

---

## See Also

- [PLAN_OF_ACTION.md](PLAN_OF_ACTION.md) — product and Nostr overview  
- [DATA_IMPORT_EXPORT.md](DATA_IMPORT_EXPORT.md) — import/export phasing and privacy notes  

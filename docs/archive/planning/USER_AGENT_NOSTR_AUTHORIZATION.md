# User-owned agents: Nostr identity, authorization, and ERV Skill

This document captures the **conceptual design** for letting users connect **their own** AI agents to Energy Radiance Vitality (ERV) without ERV hosting the agent. Use it to iterate on product behavior, security boundaries, and implementation details.

---

## Goals

- Users run agents in **environments ERV does not control** (Cursor, ChatGPT, local stacks, etc.).
- An agent has its **own Nostr identity** (separate keypair from the user).
- The user **explicitly authorizes** which agent identities may read/write **what**, and **for how long**.
- Agent-authored content **appears in the app** with clear provenance; the user is **notified** and can jump to new items.
- ERV ships a **baseline “ERV Skill”** (instructions for the agent); users may **augment** it. Optionally, ERV **auto-generates** a skill snippet from authorization settings so the agent’s instructions match policy (enforcement still lives on the server).

---

## Trust model: two identities

| Identity | Role |
|----------|------|
| **User** (`user_npub` / internal user id) | Signs or owns **user-authored** records. Agent **cannot** forge these without the user’s private key. |
| **Agent** (`agent_npub`) | Signs **agent-contributed** payloads. ERV accepts them **only** when an active **grant** links this `agent_npub` to the user with matching **permission** and **time** scope. |

**UX wording:** “Submit on my behalf” is fine. **Technical meaning:** the agent signs as **itself**; ERV **attributes** writes to the user’s account because of an **allowlist + grant**, not because the agent impersonates the user’s pubkey.

**Non-goals for the agent key:** Replacing or duplicating the user’s signing authority for user-owned events.

---

## Authorization: whitelist + scopes + time

### Core record (conceptual)

For each user, zero or more **agent grants**, for example:

- `agent_npub` — allowlisted agent identity (hex or bech32 `npub` as stored/displayed).
- `permissions` — enumerated capabilities (see below for a draft list).
- `valid_from` / `valid_until` — optional; grant invalid outside window.
- `revoked_at` — soft revoke without deleting history.
- `label` (optional) — friendly name for the agent in UI.

The **server** is the source of truth: every API path that accepts agent writes re-validates grant + scope + time.

### Draft permission dimensions (to refine)

Examples only — tighten to match real ERV APIs:

- **Read:** aggregated fitness / equipment / program data (minimize fields).
- **Write — drafts:** create or update **draft** programs or notes attributed to agent.
- **Write — publish:** promote drafts to visible “user-facing” state (if you separate draft vs published).
- **Write — events:** create calendar / session events (if applicable).
- **Delete:** only **agent-attributed** entities (never bulk-delete user-owned rows without user key or explicit separate action).

Add **rate limits** per `(user, agent_npub)` early in the design.

---

## How the agent talks to ERV

Two families of patterns (not mutually exclusive):

1. **HTTP API**  
   - Authenticate requests as the **agent** (e.g. [NIP-98](https://github.com/nostr-protocol/nips/blob/master/98.md) `Authorization: Nostr …` signed with the agent key, plus server checks URL/method/body hash and time window).  
   - Server resolves `agent_npub` → active grant → user → enforce permissions.

2. **Nostr-first ingestion**  
   - Agent publishes **agent-signed** events (specific kinds / tags TBD).  
   - ERV indexer or relay subscription ingests events, verifies sig + grant, maps into app DB.  
   - “Acceptance” might additionally be recorded as a **user-signed** event (optional audit trail on Nostr).

**Open decision:** single HTTP surface vs relay + worker vs hybrid.

### Optional: NIP-26-style delegation

[NIP-26](https://github.com/nostr-protocol/nips/blob/master/26.md) describes delegated signing with **time-bounded `created_at` conditions**. It is marked **unrecommended** in the NIPs repo but illustrates **time-capped delegation**. **Early revocation** still requires **server-side** (or published) revoke lists — do not rely on NIP-26 alone for instant policy off.

### Optional: NIP-46 / bunker

[NIP-46](https://github.com/nostr-protocol/nips/blob/master/46.md) helps users **avoid pasting `nsec`** into clients; useful for **signing grant events** or user actions, not a substitute for defining agent API policy.

---

## Product UX

### Settings: agent pubkey

- Input / paste **`agent_npub`** (validate format).
- Configure **permission scope** (checkboxes or roles).
- Configure **time scope** (expiry date, or duration from now, or indefinite with prominent risk copy).
- Show **active / expired / revoked** states and **last activity** if available.

### Notifications

- **Bell (or similar) in the upper chrome** when new **agent-attributed** content lands.
- Payload: short message + **deep link** to the entity (program, draft, event, etc.).
- Mark read / dismiss; optional “mute this agent” without revoking (product choice).

### Provenance in UI

- Surfaces that show agent-created items should read as **from the agent** (and optionally show truncated `npub`), distinct from **you**.

---

## ERV Skill: baseline + user augmentation + auto-generation

### Baseline skill

- Stable instructions: domain language, safety boundaries, how to interpret ERV concepts, how to **request** data and **submit** drafts/events **without** embedding secrets.
- Version the baseline (e.g. `erv-skill@1`) for support and changelogs.

### User augmentation

- Document a **“User context”** section users maintain (goals, injuries, equipment, schedule).  
- Prefer **append-only or clearly separated** blocks so baseline updates do not erase user notes (or ship two files: `ERV.md` + `ERV.user.md`).

### Auto-generated skill from grant settings

**Idea:** When the user saves agent settings, ERV generates a **filled-in markdown fragment** (or full `SKILL.md`) that includes:

- The **allowlisted `agent_npub`** (public — safe to include).
- **Plain-language** summary of **permissions** and **expiry** aligned with server enforcement.
- Links to **API docs** or **connection** steps (no tokens in the file).
- Reminder: **server enforces policy**; the skill is guidance for the model.

**Copy / export:** “Download skill,” “Copy to clipboard,” optional QR/deep link if target platforms support it.

**Operational notes:**

- **Regenerate** when scopes or dates change; show “last generated at.”
- **Never** embed refresh tokens, API secrets, or `nsec` in generated skills.
- Treat skill text as **best-effort**; **authorization checks** on every write are mandatory.

---

## Security and compliance (stub)

- **Least privilege:** default-deny; smallest permission set per agent.
- **Audit:** log `agent_npub`, grant id, action, resource id, timestamp.
- **Token storage:** if using OAuth to **external** fitness APIs, keep tokens on **ERV** or user-controlled vault — not in the skill file.
- **Health data:** respect App Store / Play policies and regional privacy rules; align copy with actual data flows.

---

## Implementation checklist (iterative)

- [ ] Define canonical **`agent_npub`** storage format (hex vs bech32; normalization on input).
- [ ] Grant schema + migration; revoke + expiry cron or lazy check on request.
- [ ] Permission enum aligned with real routes / resources.
- [ ] Agent auth middleware (e.g. NIP-98 verify + grant lookup).
- [ ] Notification model + deep link routes + bell UI.
- [ ] Skill template + merge with user overrides + export UX.
- [ ] E2E test: agent cannot write after revoke/expiry; cannot act as `user_npub`.

---

## Open questions

- Single **“primary”** agent vs many agents per user?
- **Draft vs published** workflow for agent-created programs?
- **User confirmation** required before agent content becomes visible to others (if any social/sharing surface exists)?
- **Nostr ingestion** vs **HTTP-only** for v1?
- How to handle **agent key rotation** (`npub` change) without losing history linkage?

---

## References (external)

- [NIP-98 — HTTP Auth](https://github.com/nostr-protocol/nips/blob/master/98.md)
- [NIP-26 — Delegated Event Signing](https://github.com/nostr-protocol/nips/blob/master/26.md) (unrecommended; illustrative for time-bounded delegation strings)
- [NIP-46 — Nostr Connect](https://github.com/nostr-protocol/nips/blob/master/46.md)
- [RFC 7009 — Token Revocation](https://www.rfc-editor.org/rfc/rfc7009) (if using OAuth for non-Nostr integrations)

---

*Last updated: initial capture from design discussion; revise in-repo as decisions land.*

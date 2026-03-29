# Companion website, web of trust, and community — benefits and pitfalls

This note complements [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md), which describes the **product vision** for a trust-filtered protocol graph and why a **full website** (not only in-app screens) is a natural fit for discovery, comparison, and forking. The [plan of action](PLAN_OF_ACTION.md) focuses on **mobile app data model, Nostr events, and implementation phases**; it does **not** spell out a companion web product in the same depth. Here we focus on **what could go right or wrong** if ERV adds that layer specifically for **fitness promotion** and **community**.

---

## Potential benefits

**Adherence and motivation** — Seeing routines that trusted people actually use can increase follow-through compared to anonymous templates. Social proof within a bounded graph can feel motivating without turning the product into a performative feed.

**Skill transfer** — Structured routines (sets, progressions, constraints) travel better than screenshots or chat advice. A web UI for diffing and forking supports learning *how* someone adapted a program, not only *what* they posted once.

**Lower noise than open social** — A web-of-trust filter reduces exposure to spam, scams, and drive-by bad advice relative to a global directory or unfiltered hashtag culture—aligned with [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md) §2–§3.

**Community without a central curator** — Growth does not depend on a single editorial voice. Trust edges and lineage (`parent_id`, version history) let the network improve content through forks rather than through top-down approval alone.

**Bridge to the app** — If the site is the place to browse, compare, and import or fork, and the app remains where users **execute and log**, each surface does what it is good at: discovery and editing on the web; sensors, timers, and daily truth on the device.

**Fitness promotion (non-coercive)** — Emphasis on adaptation, context, and trusted peers can support sustainable behavior change better than viral challenges that optimize for engagement over safety.

---

## Potential pitfalls

**Medical and safety liability** — Fitness content sits between wellness and clinical risk. Sharing programs at scale—even trust-filtered—can expose users to overtraining, contraindicated movements, or supplement interactions. Clear disclaimers, structured warnings on routines, and **not** treating trust as medical endorsement are essential (see [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md) §8).

**Echo chambers and blind spots** — Web of trust surfaces *familiar* networks. That reduces junk but can also hide diversity of methods, underrepresented bodies, or expert corrections when the graph is homogeneous. Mitigations: optional bounded exploration, category-specific trust, and UI that distinguishes “trusted for me” from “universally correct.”

**Gaming trust** — Any visibility signal invites Sybil-like behavior, reciprocal trust inflation, or cult-of-personality dynamics. Trust must stay **one input among several** (reuse history, reports, account age, as sketched in PROTOCOL_GRAPH §8), not a single score that dominates ranking.

**Product split** — Two surfaces (web + app) add sync, identity, and UX consistency costs. If import into the profile is clumsy, users will copy-paste unstructured text and defeat the purpose of structured protocols.

**Regulatory and platform optics** — Depending on jurisdiction, “recommending” exercises or supplements to others may fall under health-claim rules. A decentralized or Nostr-backed design does not remove responsibility for how the **official** ERV web client presents and ranks content.

**Community moderation load** — Even with WOT, harmful content (eating-disorder framing, unsafe challenges) may appear within trusted subgraphs. The project needs a stance on reporting, downranking, and legal escalation without abandoning the open-protocol ethos.

**Engagement vs. quality** — Community features often drift toward notifications, streaks, and vanity metrics. That can undermine the thesis of “context over hype” ([PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md) §12). Explicit product principles help resist that drift.

---

## Summary

A companion website with web-of-trust discovery and forkable routines could **strengthen adherence, learning, and community** while keeping execution local-first in the app—but it carries **safety, echo-chamber, gaming, and split-product** risks that should be weighed alongside the vision in [PROTOCOL_GRAPH.md](PROTOCOL_GRAPH.md). Treat this document as a decision aid, not a commitment to build any particular scope.

# ERV Protocol Graph Notes

This document captures the early thinking behind a protocol-sharing layer for ERV. The goal is not to create a public feed where everyone sees everything. The goal is to let users see what people in their web of trust are doing, usually within one hop, and adapt those routines to their own needs.

The working idea is that ERV becomes a place where health routines can be observed, copied, forked, and refined in a socially filtered graph rather than in a flat marketplace.

ERV is a trust-filtered health protocol graph where people can discover, fork, and adapt routines from their web of trust, preserving lineage and context instead of just copying advice blindly.

---

## 1. Core idea

People do not mainly want expert authority. They want to see what other people they trust are actually doing, in context, and then adapt it.

The app should support:

- routine discovery from trusted people
- easy copying or forking of a routine
- editing the copied routine for personal needs
- tracking what changed and what happened afterward
- sharing the adapted version back into the graph

This is less like a traditional marketplace and more like a living map of health protocols.

## 2. Visibility model

The relay does not need to be a place where every post is equally visible to every user.

A better model is:

- show content from people in the user’s web of trust
- optionally show content from 1 trust hop away
- rank content by trust, similarity, and relevance
- hide or downrank suspicious, spammy, or low-signal accounts

That means the relay can hold the data, but the application decides what is surfaced in the UI.

## 3. Why WOT matters

Web of trust can help reduce scams and low-quality content, but it should be treated as one signal, not a truth machine.

Useful roles for WOT:

- prioritize routines from trusted people
- reduce exposure to obvious spam or bad actors
- make discovery feel social instead of random
- bootstrap a community without requiring formal experts

WOT should influence visibility and confidence, not automatically declare something correct.

## 4. Why this should be a website

This likely needs to be a fully functional website, not just a lightweight app screen or relay viewer.

Reasons:

- users need to browse routines comfortably
- users need to compare versions and edits
- forking and remixing routines needs a richer UI
- graph navigation is easier on the web
- it can act as the public front door for ERV

If this works, the website becomes the place where people discover and shape routines, while the mobile app remains the place where they execute and log them.

## 5. What a protocol object should contain

A routine should not just be a blob of text. It should be structured enough to reuse, compare, and fork.

Suggested minimum fields:

- `id`
- `title`
- `category`, such as supplements, workout, sleep, sauna, cold plunge, cardio, or mixed
- `goal`
- `creator`
- `created_at`
- `updated_at`
- `trust_context`
- `time_commitment`
- `required_equipment`
- `steps` or `items`
- `rationale`
- `constraints_or_warnings`
- `outcome_notes`
- `parent_id` or `source_ref`
- `version`

The important part is that the routine can be forked without losing its history. A fork should preserve the parent relationship so users can see where it came from and how it changed.

## 6. Forking and adaptation

The main user action should be something like:

1. View a trusted routine.
2. Inspect why it exists and who is using it.
3. Fork it into your own version.
4. Edit the routine for your body, schedule, budget, equipment, and goals.
5. Track what changed from the parent.
6. Optionally publish the adapted version back into the graph.

The fork should not just duplicate text. It should create a new node with a clear lineage and a change summary.

Useful adaptation metadata:

- what was removed
- what was added
- what was reordered
- what dosage, time, or duration changed
- whether the user has actually tested the modified version
- whether the result was better, worse, or unknown

This creates a real learning loop instead of a static content feed.

## 7. Graph behavior

The graph should be treated as a trust-filtered recommendation system, not a universal directory.

Suggested behavior:

- default view: direct trust neighbors first
- secondary view: 1-hop neighbors
- optional exploration: broader network, but clearly labeled
- downrank content from unknown or suspicious accounts
- allow category-specific trust, so someone trusted for workouts is not automatically trusted for supplements

The relay can hold everything, but the UI should decide what to surface and how strongly to rank it.

## 8. Trust model notes

WOT should be a ranking and safety signal, not a proof of correctness.

Practical trust signals could include:

- direct trust edges
- number of trusted neighbors who also follow the source
- account age
- routine reuse history
- report history
- category-specific reputation

This gives the system a way to prefer credible routines without pretending that credibility means perfection.

## 9. Why the website matters

This likely needs to be a fully functional website, not just a lightweight app screen or relay viewer.

Reasons:

- users need to browse routines comfortably
- users need to compare versions and edits
- forking and remixing routines needs a richer UI
- graph navigation is easier on the web
- it can act as the public front door for ERV

The website should be the place where people discover, inspect, compare, and fork routines. The mobile app can remain the execution layer for logging and daily use.

## 10. Website capabilities

The website should probably support:

- sign in with Nostr or remote signer
- browse a trust-filtered feed
- open a routine detail page
- compare a routine with its parent
- fork a routine into a draft
- edit routine steps and context
- publish a new version
- view trust paths and provenance
- filter by category, goal, and similarity
- search within the trusted graph

If the graph idea is the product, then the web app is not optional. It is the primary interface for understanding and reshaping routines.

## 11. Suggested MVP

The first version should be narrow.

Good first category choices:

- supplements
- sleep
- workouts

The MVP should include:

- trust-filtered feed
- routine detail page
- fork button
- edit draft
- publish fork
- basic lineage display

What to avoid in v1:

- open-ended social posting
- fully public browsing with no trust filter
- too many health categories at once
- heavy creator tooling
- algorithmic recommendations that ignore trust

## 12. Product risk

The biggest risk is that the system turns into a noisy social feed full of unstructured advice.

To avoid that, ERV should emphasize:

- context over hype
- adaptation over authority
- trust over virality
- structure over posting volume
- lineage over one-off posts

If the product becomes mostly content without usable structure, it loses the point.

## 13. Open questions

- How many hops away should content remain visible by default?
- Should trust be global, per-category, or both?
- How do we represent a fork cleanly in the data model?
- What is the minimum structure needed for a routine to be reusable?
- Should public discovery exist at all, or only within trust boundaries?
- How much of this belongs in the website versus the mobile app?
- Should users be able to follow a person, a routine, or both?
- Should a fork inherit trust from its parent, or start fresh?

## 14. Working thesis

ERV could become a socially filtered health protocol network where users do not follow experts blindly, but instead discover what trusted people are doing, adapt it to their own lives, and improve it over time.

That is the behavior this system should optimize for.

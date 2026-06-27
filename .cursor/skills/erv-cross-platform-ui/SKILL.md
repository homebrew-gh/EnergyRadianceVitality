---
name: erv-cross-platform-ui
description: Keeps ERV web and Android UI consistent — theme tokens, form labels, section headers, buttons, and cards. Use when adding or editing forms, settings screens, workout/routine editors, labels, styling, Compose UI, React/Tailwind UI, or design parity between apps/web and app.
---

# ERV cross-platform UI

ERV ships two surfaces that should feel like one product:

- **Web:** React + Tailwind — `apps/web/web/`
- **Android:** Jetpack Compose + Material 3 — `app/src/main/java/com/erv/app/ui/`

## Theme tokens (keep in sync)

| Concern | Web | Android |
|---------|-----|---------|
| Palette source | `apps/web/web/src/index.css` (`--erv-*` CSS vars) | `app/src/main/java/com/erv/app/ui/theme/Color.kt` |
| Body background | `--erv-bg`, `--erv-body-gradient` | Material theme background |
| Primary / accent | `--erv-primary`, `--erv-secondary` | `ErvPrimary`, `ErvSecondary` |
| Cards | `.card` class | `Card` + surface colors |
| Buttons | `.btn-primary`, `.btn-ghost` | `Button`, `FilledTonalButton`, `TextButton` |
| Text inputs | `.input` | `OutlinedTextField` |

When changing a color or surface treatment, update **both** token files unless the change is deliberately platform-specific.

## Field labels and section headers (required)

Multi-word **field captions** and **section group headings** must use title case. Never use `uppercase`, `tracking-wide`, or ALL CAPS for these.

This is also enforced by `.cursor/rules/ui-label-title-case.mdc` (always applied).

### Web

Use shared helpers — not raw text in labels:

```tsx
import { FieldLabel, SectionHeader } from "../components/FieldLabel";

<label className="label" htmlFor="name">
  <FieldLabel>Workout name</FieldLabel>
</label>

<SectionHeader>Rest between sets</SectionHeader>
```

Source strings may stay lowercase; helpers call `titleCaseWords()`.

Prefer `.label` for field captions. Do not use `tracking-wide` or ALL CAPS on section labels (`.erv-section-label` is legacy — prefer `SectionHeader`).

### Android

Use shared composables — not raw `Text("multi word …")` for captions:

```kotlin
import com.erv.app.ui.components.FieldLabel
import com.erv.app.ui.components.FormSectionLabel
import com.erv.app.ui.components.FormSectionLabelMedium
import com.erv.app.ui.components.SectionHeader

// OutlinedTextField label
label = { FieldLabel("Rest between sets (seconds)") }

// Section above a control group
FormSectionLabel("Muscle group")
FormSectionLabelMedium("Log prompts")
FormSectionLabelSmall("Rest between sets")
SectionHeader("Saved Bluetooth devices")
```

For **dynamic** label strings (exercise names, user text), use plain `Text` without title-casing.

### Do not title-case

- Button text, dialog titles, page titles, nav labels, toasts
- Dynamic content (exercise names, routine names, user-entered text)
- Single-word labels

## Web layout patterns

Reuse existing structure instead of one-off page layouts:

| Pattern | Path |
|---------|------|
| Routine/workout builder shell | `apps/web/web/src/components/RoutineBuilderLayout.tsx` |
| Library sidebar | `apps/web/web/src/components/LibrarySidebar.tsx` |
| Saved routines panel | `apps/web/web/src/components/SavedRoutinesPanel.tsx` |
| Reorderable lists | `apps/web/web/src/components/ReorderableList.tsx` |
| Auth card | `apps/web/web/src/components/AuthCard.tsx` |

## Android layout patterns

- Shared label composables: `app/src/main/java/com/erv/app/ui/components/ErvLabel.kt`
- Follow existing silo screen structure under `app/.../ui/<silo>/`
- Prefer `ModalBottomSheet`, `FilterChip`, `Card` patterns already used in silo editors

## Parity checklist (new form UI)

- [ ] Web uses `FieldLabel` / `SectionHeader` for multi-word captions
- [ ] Android uses `FieldLabel` / `FormSectionLabel*` for the same captions
- [ ] Colors come from token files, not hard-coded hex in components
- [ ] Web uses `.card`, `.btn-primary`, `.input` (or equivalent Tailwind + `--erv-*`)
- [ ] Android uses Material 3 theme colors, not one-off `Color(0xFF…)` unless matching a token

## When only one platform changes

If a feature is web-only (Start9 companion) or Android-only (live run sensors), document why in the PR. Do not silently diverge shared concepts (routine fields, workout segment names, catalog labels).

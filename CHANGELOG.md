# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Phone check in the web builder: the workout preview shows a badge with how many places the phone runs the workout differently from what's written, and lists each under its section. It covers superset and circuit lifts whose set count, per-set targets or own rest the phone ignores, non-lift items in a superset or circuit, and interval legs the phone rounds up to whole minutes. JSON import shows the same warnings before publishing.
- Android release GitHub Actions workflow: builds, signs and publishes a release from the Actions tab so devices can update through Obtainium.
- Start9 release GitHub Actions workflow: builds, bumps, signs and publishes the StartOS `.s9pk` as a `start9-v*` release from the Actions tab.
- Plate calculator on barbell lifts, using the bars and plates under Equipment (a standard gym set until you add yours). The phone shows plates per side under each set with a bar picker that remembers your bar per exercise; the web builder shows them under the target weight. Weights you can't load offer the nearest loadable weights. Both read one shared file of test cases so they always agree.

### Fixed

- Rest timer keeps going in the background: rest between sets is kept with the live session, so leaving the screen, rotating or the app being closed no longer drops it. When rest ends with the phone locked or ERV in the background, a "Rest over" notification sounds and vibrates; on screen, rest end now vibrates too. Lifting inside a workout shows its ongoing notification, titled with the workout, and the notification counts down the rest. Rests between workout steps on the run screen get the same alert.
- One Start tap: Start Workout on the Training tab starts the run, with no second Start on the run screen. Backing out within a minute, before anything is logged, just leaves. The hero card says Resume Workout while that workout is running and shows Completed once it's logged that day, the week strip checks days you trained, and a run in progress gets a Resume card on any day. The 5 second full-screen splash between sections is now a 3 second banner with Start Now and Not Yet.
- Live workout set rows are numbered and have a check. Checking a set logs it, filling empty reps and weight from the target, so a set done as planned is one tap. The rest timer starts on the check instead of the first digit typed or Add Set, and counts down automatically where the workout prescribes rest until you pick a rest mode.
- Weight and RPE fields keep what you type, so decimals like 2.5 or 137.25 can be entered. Whole pounds no longer show as "145.0", quarter values keep both decimals, and a comma decimal separator is accepted.
- Pounds everywhere: the phone run screen and builder show target loads in your chosen unit instead of kg, exercise volume reads "lb·reps", and the builder's baseline and progression loads round to 2.5 lb with 2.5, 5 or 10 lb steps.

## [2026.06.20] - 2026-06-20

### Added

- Multi-modality workout composer: segments for flow, cardio, intervals, mobility, and lifting (straight sets, circuits, supersets).
- Workout library sync (`erv/workouts/library`) with live run, rest timers, and exercise alternatives at run time.
- JSON workout import from Settings.
- Title-case field labels and section headers across Android forms (`FieldLabel`, `FormSectionLabel`).

### Changed

- Lifting prescriptions: max reps, per-side reps, per-set editor, and alternative exercises.
- Workout composer polish: segment collapse, duplicate workout, segment drag-reorder.
- Disabled Android cloud backup and Android device-to-device extraction for app data.
- Clarified privacy, signer-trust, relay-upload, and data-location messaging across Settings and privacy docs.
- Added support/contact links in Settings and expanded repository contact documentation.
- Added Zapstore release-prep docs, including a store-listing draft and manual release test checklist.

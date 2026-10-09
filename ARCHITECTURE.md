# TrialTracker — Architecture

Implementation notes for anyone reading or building on this codebase. See [README.md](README.md) for user-facing
docs, [CONTRIBUTING.md](CONTRIBUTING.md) for contribution status, and [LICENCE](LICENCE) for licensing.

## Principles

Keep the architecture simple and avoid excess abstraction, but stay generic enough to reuse for future trials via
an imported FHIR-shaped `ServiceRequest` and `Questionnaire`. No DI framework, no navigation graph — there are very
few screens and they're basically siblings. No plugins, no cloud, no login.

`Room` for persistence, `Compose` for UI.

## Module map

- `data/` — Room entities, DAOs, `TrialDatabase`.
- `questionnaire/`, `servicerequest/` — FHIR-shaped DTOs, parsing, and the flattened domain types built from them.
- `trialsetup/` — the import flow (pick file → read → parse → confirm → persist) and startup loading.
- `export/` — JSON/CSV export of recorded entries.
- `summary/` — per-day completion status used by the calendar view.
- `ui/` — screens and their ViewModels (`ui/entry/` for the daily-entry screen specifically).

## Data model (Room)

Three tables: `entries` and `trials` are dynamic (JSON blobs, since their real structure is only known once a
questionnaire is imported); `settings` is static (typed columns, evolved via `@AutoMigration` with explicit
`@ColumnInfo(defaultValue = ...)` on new columns).

JSON is parsed on every app start rather than at import time — easier to evolve than storing parsed objects, at
the cost of a parse on each launch. Given the project's early state this tradeoff was chosen deliberately over the
alternative (parse once at import, store objects, but then DB shape has to evolve with the format).

`trials` currently only ever has 0 or 1 rows (fixed id `TrialRecord.ACTIVE_ID`), though the schema doesn't enforce
that — it's a policy choice, not a structural one, made so the table can later support sequential/concurrent
trials without a migration.

## Key runtime concepts

- **`LiveTrial`** — not a Room entity; a plain class parsed once per active trial from `TrialRecord`'s JSON
  columns, exposing the questionnaire and derived fields for convenience. This, not `TrialRecord`, is what flows
  through `MainActivity` and `TrialTrackerApp`.
- **Import ordering** — a `ServiceRequest` must be loaded before a `Questionnaire` can be. Enforced by a single
  guard in `TrialSetupViewModel`, not by the schema (both JSON columns are independently nullable).

### Setup states

Splash screen stays up while `TrialSetupViewModel` reads the singleton `TrialRecord` row (if any) and computes
`activeTrial` (non-null only once *both* artifacts are imported):

- **Not yet configured** — no `TrialRecord` row, or a row with only `serviceRequestJson` set. `activeTrial` is
  null either way. There's no explicit routing lock; instead every drawer destination except *Trial &
  Questionnaire* and *Help* is disabled (dimmed, no-op) while `activeTrial` is null, so in practice the user has
  nowhere else to go.
- **Configured** — both artifacts imported, `activeTrial` non-null, full navigation opens up. The transition from
  null → non-null fires a one-shot "Congratulations" dialog offering to jump straight to Entry.

Both raw JSON texts are stored verbatim in `trials` on confirm — no reformatting or canonicalisation.

The landing screen is decided once, at launch, from `activeTrial`'s nullity at that moment (`Entry` if non-null,
else *Trial & Questionnaire*) — it isn't live-reactive to `activeTrial` changing afterwards.

## Screens

`Entry`, `Summary`, `TrialAndQuestionnaire`, `Help`, `Settings` — a sealed `Screen` class plus a single
`mutableStateOf<Screen>` in `TrialTrackerApp`, dispatched with a `when`. No nav-graph, no back stack; the drawer
sets `currentScreen` directly.

- **Entry** — daily data entry, autosaved per keystroke.
- **Summary** — calendar view of the trial so far.
- **Trial & Questionnaire** — load/review the ServiceRequest and Questionnaire.
- **Settings**, **Help**.
- **Export results** isn't a screen — a drawer action that launches the system file-save picker directly.

## Configuration file format

The app is configured from two FHIR-shaped JSON files, `ServiceRequest` and `Questionnaire`. See
[samples/](samples) for real examples; the formal field-level definition lives in `FieldDef.kt`.

## Build

- **AGP 9.4.0 requires Gradle 9.6.0+.** Wrapper is pinned to 9.6.1 — update `gradle-wrapper.properties`'
  `distributionSha256Sum` together with the version when bumping Gradle (checksums:
  https://gradle.org/release-checksums/).
- **Don't apply `org.jetbrains.kotlin.android` explicitly** — AGP 9.x has built-in Kotlin support, and applying it
  yourself causes a duplicate-extension error. Only `kotlin.compose`, `kotlin.serialization`, and `ksp` are applied
  alongside `com.android.application`.
- **KSP + AGP built-in-Kotlin source-set bug**: if you see `Using kotlin.sourceSets DSL to add Kotlin sources is
  not allowed with built-in Kotlin`, add `android.disallowKotlinSourceSets=false` to `gradle.properties`. Open
  upstream gap as of AGP 9.4 / KSP 2.3.x.
- **KSP versions independently of Kotlin as of KSP 2.3.0** — no more version pairing; check
  https://github.com/google/ksp/releases for the current release.
- **`android.newDsl=false`** (in `gradle.properties`) works around `kotlin-language-server`/`lsp-mode` failing to
  resolve the classpath against AGP 9.x's new DSL (Android Studio itself is unaffected). Likely temporary — AGP is
  expected to drop the old DSL in AGP 10.
- **Command-line Gradle needs `JAVA_HOME` set to Android Studio's bundled JBR**, not the system JDK, to match the
  IDE. Snap install path: `/snap/android-studio/<revision>/jbr` — re-point after Android Studio updates. Current
  version: JBR 25 (periodically check via Android Studio → About). CI currently pins JDK 25 (Temurin). Keep them in sync.
- **Command-line Gradle needs `JAVA_HOME` set to Android Studio's bundled JBR**, not the system JDK, to match the
  IDE. Snap install path: `/snap/android-studio/<revision>/jbr` — re-point after Android Studio updates.

## Automated testing

Unit tests cover each module (`data`, `export`, `questionnaire`, `servicerequest`, `summary`, `trialsetup`, `ui`),
plus a samples-load test that parses everything in `samples/`.

Migrations are tested via `androidx.room:room-testing`'s `MigrationTestHelper`
(`androidTest/.../data/TrialDatabaseMigrationTest.kt`), which seeds a DB at an old schema version, runs a named
`Migration`, and asserts on the result — no device/emulator round-trip needed. May be empty if the migration set
has since been flattened.

## Linting

Spotless + ktlint 1.8.0, default rules except two deliberate exceptions:
- `TrialDatabase.kt`'s `INSTANCE` field is exempted from `property-naming` (standard double-checked-locking
  singleton convention).
- `@Composable` functions are exempted from `function-naming` via `editorConfigOverride` in `app/build.gradle.kts`
  — PascalCase is the standard Compose convention and would otherwise be flagged on every `@Composable fun`.

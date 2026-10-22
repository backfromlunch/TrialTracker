# Engineering Plans

## Initial startup
 - [] validate Questionnaire being loaded as being the one referred to by ServiceRequest
 - [] change the 'congratulations' dialog. allow either 'JUMP TO ENTRY' or 'stay here to review'.

## FHIR related
 - [] Do we need 'date' in the questionnaire? is it implicit? implement hidden=true
 - [] Find mechanism to indicate AM / lunch /PM against questions
 - [] Alerts: for example, for fields which need to be filled in at a specific time of the day e.g. 12 noon.
 - [] servicerequest config: e.g. 'allow past to be viewed/edited<bool>'
 - [] questionnaire config: specify which fields are 'optional when considering whether a day has been "completed"'
 - [] 'Copy yesterday button': but this should be gated on a config flag (in many trials it's better not to see previous entries.

## Multi-trial support
DB architecture allows for multiple tials, but (by design) impementation currently only ever surfaces exactly one "active" trial at a time
 - [] UI to list, choose, or manage multiple trials.

## Per-type field logic
Idea: move per-type behaviour into the `FieldDef` subclasses (`fields/`), so a new type is one file plus one composable.
 - [] `FieldDef.normalise(stored)`: what a save writes (counter: unset → `[]`). Replaces `withZeroCounters`.
 - [] `FieldDef.isCompleted(stored)`: replaces the per-type check in `DayStatus`.
 - [] `FieldDef.export(stored): ExportValue`: a neutral value (None, Integer, Text, Coded, Events, ...). `CsvExporter` and `JsonExporter` each format it, so `FieldDef` knows nothing of CSV or FHIR.
 - [] A recogniser per subclass, replacing the `when` in `FieldDefSerializer`.
 - [] Per-type files: `fields/IntegerField.kt` ... `CounterField.kt` (class + answer logic; absorbs `CounterAnswer.kt`), `fields/ExportValue.kt`.
 - [] `ui/entry/field/`: one composable per type. Compose stays out of `FieldDef`; the UI keeps one exhaustive `when`.

## Other
  - [] can we load servicerequest directly from a qrcode? (may need compression)

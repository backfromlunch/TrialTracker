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

## Other
  - [] can we load servicerequest directly from a qrcode? (may need compression)

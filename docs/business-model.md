# Business Model: Residential care activities for the elderly and disabled

## Classification

- Repository: `cloud-itonami-isic-8730`
- ISIC Rev.5: `8730`
- Activity: residential care activities for the elderly and disabled -- assisted living and supportive housing without the level of nursing care in residential nursing facilities
- Social impact: care quality, data sovereignty, transparent audit

## Customer

- independent assisted-living operators
- cooperative eldercare communities
- community disability-support residences

## Offer

- resident intake
- care-plan proposal
- incident-response proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per facility
- support: monthly retainer with SLA
- migration: import from an incumbent assisted-living system
- per-resident-month fee

## Trust Controls

- no care plan or incident response is finalized without human
  sign-off (a facility administrator/care manager)
- a fabricated jurisdiction citation, incomplete resident evidence, a
  care-plan review that has exceeded its own regulatory ceiling, or an
  unresolved incident flag -- each forces a hold, not an override
- a resident's care plan/incident response cannot each be finalized
  twice: a double-finalization attempt is held off this actor's own
  resident facts alone, with no upstream comparison needed
- every intake, assessment, screening and finalization path is
  auditable
- resident data stays outside Git
- emergency manual override paths remain outside LLM control

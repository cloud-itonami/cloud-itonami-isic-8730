# ADR-0001: cloud-itonami-isic-8730 -- EldercareOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-07)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603`/`9521`/`9321` ADR-0001s (the pattern this ADR ports);
  ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922
  (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/
  `9603`/`9521`/`9321`, the twelve verticals built outside
  ADR-2607032000's original insurance/real-estate batch -- this is the
  thirteenth)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `9321`, this ADR deepens `cloud-itonami-
  isic-8730` (residential care activities for the elderly and
  disabled) from `:blueprint` to `:implemented`, the twenty-first actor
  in this fleet -- a SECOND social/health-services vertical (ISIC
  division 87), alongside `8620`'s clinic.

## Problem

An assisted-living facility's care-plan/incident-response finalization
workflow bundles several distinct concerns under one governed
workflow:

1. **Jurisdiction assisted-living correctness** -- an official
   spec-basis citation from a real regulator (MHLW/California DSS
   Community Care Licensing Division/CQC/Heimaufsichtsbehörden der
   Länder), never fabricated.
2. **Care-plan review timeliness** -- has a resident's own periodic
   care-plan review been kept current, or has it exceeded the
   regulatory review-interval ceiling? The FIRST check in this fleet's
   temporal-sufficiency family to enforce a MAXIMUM elapsed-time
   ceiling ("not too much time may pass before the next review")
   rather than a MINIMUM required wait (`veterinary.registry/
   withdrawal-period-insufficient?`'s/`funeral.registry/waiting-
   period-elapsed?`'s direction).
3. **Incident-flag resolution verification** -- has a resident's
   incident flag actually been resolved before either a care plan or
   an incident response is finalized? The residential-eldercare-
   specific reuse of the unconditional-evaluation screening discipline
   this fleet's `casualty.governor/sanctions-violations` originally
   established -- a TENTH distinct grounding.
4. **Real, high-stakes actuation, twice** -- finalizing a real
   resident's care plan and finalizing a real resident's incident
   response are two independently-gated real-world acts on the SAME
   entity (a resident).

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run an assisted-living facility with an
LLM" but "seal the LLM inside a trust boundary and layer evidence-
sufficiency, review-timeliness verification, incident-flag-resolution
verification, audit and human-approval on top of it, while
structurally fixing both real actuation events as human-only."

## Decision

### 1. EldercareOps-LLM is sealed into the bottom node; it never finalizes directly

`eldercare.eldercareopsllm` returns exactly five kinds of proposal:
intake normalization, jurisdiction assisted-living checklist, incident
screening, care-plan-finalization draft, and incident-response-
finalization draft. No proposal writes the SSoT or commits a real
finalization directly.

### 2. OperationActor = langgraph-clj StateGraph, 1 run = 1 residential-eldercare operation

`eldercare.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 3. `care-plan-review-overdue?` is the FIRST check in this fleet's temporal-sufficiency family to invert the direction to a MAXIMUM ceiling

Every prior temporal-sufficiency check in this fleet (`veterinary.
registry/withdrawal-period-insufficient?`, `funeral.registry/waiting-
period-elapsed?`) enforces a MINIMUM required wait: enough time must
pass before an irreversible act may proceed. `care-plan-review-
overdue?` inverts this: a resident's own `:days-since-last-care-plan-
review` must NOT exceed `max-review-interval-days` (90) -- too much
time must NOT have passed since the last review. This is a genuinely
new direction for the family, not a repeat of the existing shape --
"has enough time passed" and "has too much time passed" are distinct
failure modes requiring distinct comparison operators (`<` vs. `>`)
against the same kind of ground-truth elapsed-time field.

### 4. Incident-flag-unresolved screening reuses the unconditional-evaluation discipline for a tenth distinct grounding

`incident-flag-unresolved-violations` reuses `casualty.governor/
sanctions-violations`'s fix (evaluated unconditionally, not scoped to
a specific op, so the screening op itself can HARD-hold on its own
finding) for `:incident/screen`, `:care-plan/finalize` AND
`:incident-response/finalize` -- the TENTH distinct application of
this exact discipline in this fleet, and the first to gate BOTH
actuation ops of a dual-actuation actor off the SAME unresolved-flag
concept.

### 5. The unconditional-evaluation check is tested via the SCREENING op directly, per the lesson `parksafety`'s ADR-2607071922 Decision 5 already recorded

`incident-flag-unresolved-is-held-and-unoverridable` calls `:incident/
screen` directly against `resident-4` (an unresolved incident flag),
NOT `:care-plan/finalize`/`:incident-response/finalize` against an
un-screened resident -- because a failing screen is itself a HARD hold
whose payload never persists to the store, so the actuation ops alone
could never discover the bad ground-truth flag through this check
family without the screening op having actually been run first. This
build applied that lesson PROACTIVELY (writing the correct test from
the start) rather than discovering it the hard way -- the same kind of
proactive-transfer `veterinary.registry/withdrawal-period-
insufficient?`'s type-tag-gated design already demonstrated for the
`accounting.governor` lesson.

### 6. Dual actuation, matching `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`'s shape

`eldercare.governor`'s `high-stakes` set has exactly two members
(`:actuation/finalize-care-plan`, `:actuation/finalize-incident-
response`), each acting on the SAME resident entity, each with its OWN
history collection (`care-plan-history`/`incident-response-history`),
sequence counter and dedicated double-actuation-guard boolean.

### 7. Double-finalization guards check dedicated booleans, not `:status`

`already-care-plan-finalized-violations`/`already-incident-response-
finalized-violations` check `:care-plan-finalized?`/`:incident-
response-finalized?`, dedicated booleans set once and never cleared,
rather than a `:status` value that could legitimately advance past a
checked state (the exact trap `cloud-itonami-isic-6492`'s ADR-0001
documents in detail, explicitly avoided BY DESIGN in every sibling
actor's equivalent guard since). This actor's `:status` never needs to
encode "has this actuation already happened" at all -- a deliberate
architectural choice applied here for an eleventh consecutive time.

### 8. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`,
and unlike most other actors in this fleet, this vertical's resident
records are practice-specific rather than a shared cross-operator data
contract -- `eldercare.*` runs on the generic identity/forms/dmn/bpmn/
audit-ledger stack only, per the blueprint's own explicit statement.

## Consequences

- (+) Residential eldercare gets the same governed, auditable-actor
  treatment as the twenty prior actors, extending the pattern to a
  second social/health-services vertical (ISIC division 87).
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/eldercare/phase_test.clj`'s `care-plan-
  finalize-never-auto-at-any-phase`/`incident-response-finalize-never-
  auto-at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/eldercare/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses.
- (+) `care-plan-review-overdue?` genuinely extends this fleet's
  temporal-sufficiency family with a new direction (MAXIMUM ceiling,
  not MINIMUM wait) rather than repeating an existing shape.
- (+) The incident-flag-unresolved test/demo design correctly followed
  the SCREENING-op-directly pattern from the start, applying
  `parksafety`'s freshly-documented lesson proactively rather than
  re-discovering it.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `eldercare.facts/
  coverage` reports this honestly rather than claiming broader
  coverage.
- (-) `max-review-interval-days` models a single representative
  regulatory review-interval ceiling (90 days), not a jurisdiction-by-
  jurisdiction survey of every review-cadence variant, nor a full
  care-management/staffing-acuity program (clinical-acuity scoring,
  staffing-ratio engine, medication-management workflow are out of
  scope -- see that fn's own docstring); real facility-management-
  system integration and ongoing day-to-day care-delivery workflows are
  all out of scope for this OSS actor -- each operator's responsibility
  (see README's coverage table).
- 37 tests / 175 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to any prior post-batch ADR | ❌ | All twelve of those ADRs' titles and scopes are explicitly `cloud-itonami-isic-6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`; mixing a different ISIC division (87, distinct from most of those twelve's divisions) into any would blur scope boundaries |
| Keep `cloud-itonami-isic-8730` at `:blueprint` only | ❌ | The standing direction continues past `9321`; residential eldercare is a natural, well-precedented next domain, further diversifying this fleet's social/health-services coverage alongside `8620`'s clinic |
| Model `care-plan-review-overdue?` as a MINIMUM-wait check (reusing veterinary/funeral's direction unchanged) | ❌ | Would misrepresent the actual regulatory concern -- assisted-living care-plan reviews must happen FREQUENTLY ENOUGH, not be DELAYED long enough; the honest check is a MAXIMUM ceiling, a genuinely new direction worth documenting distinctly |
| Test `incident-flag-unresolved-violations` via an actuation op against an un-screened resident (the shape `parksafety`'s ORIGINAL, buggy test used) | ❌ | Already proven wrong by `parksafety`'s ADR-2607071922 Decision 5 -- a failing screen never persists its payload to the store, so the actuation op alone cannot discover the bad ground-truth flag through this check family; this build tested the SCREENING op directly from the start |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/eldercare`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning established by every "no bespoke capability lib" sibling's ADR |

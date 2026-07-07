# cloud-itonami-isic-8730

Open Business Blueprint for **ISIC Rev.5 8730**: Residential care
activities for the elderly and disabled. This repository publishes a
residential-eldercare actor -- resident intake, jurisdiction
assessment, incident screening, care-plan finalization and incident-
response finalization -- as an OSS business that any qualified,
licensed assisted-living facility operator can fork, deploy, run,
improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321)) --
a second social/health-services vertical (ISIC division 87) in this
fleet, alongside `8620`'s clinic. Here it is **EldercareOps-LLM ⊣
Eldercare Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> resident-intake summary, normalizing records, and checking whether a
> resident's own days-since-last-care-plan-review has crept past the
> regulatory review ceiling -- but it has **no notion of which
> jurisdiction's assisted-living licensing requirements are official,
> no license to finalize a real resident's care plan or incident
> response, and no way to know on its own whether a resident's own
> incident flag is still unresolved**. Letting it finalize a care plan
> or incident response directly invites fabricated jurisdiction
> citations, a care plan finalized well past its own regulatory review
> ceiling, and an unresolved incident being quietly signed off -- and
> liability, and resident-safety risk, for whoever runs it. This
> project seals the EldercareOps-LLM into a single node and wraps it
> with an independent **Eldercare Governor**, a human **approval
> workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers resident intake through jurisdiction assessment,
incident screening, care-plan finalization and incident-response
finalization. It does **not**, by itself, hold any license required to
operate an assisted-living/residential-care facility in a given
jurisdiction, and it does not claim to. It also does **not** model a
full care-management/staffing-acuity program -- no clinical-acuity
scoring, no staffing-ratio engine, no medication-management workflow
(see `eldercare.registry/max-review-interval-days`'s own docstring for
the honest simplification this makes: a single representative maximum
review-interval ceiling, not a jurisdiction-by-jurisdiction survey of
every review-cadence variant). Whoever deploys and operates a live
instance (a licensed facility operator) supplies the jurisdiction-
specific license, the real clinical/care-management expertise and the
real facility-management-system integrations, and bears that
jurisdiction's liability -- the software supplies the governed, spec-
cited, audited execution scaffold so that operator does not have to
build the compliance layer from scratch for every new market.

### Actuation

**Finalizing a real resident's care plan or incident response is never
autonomous, at any phase, by construction.** Two independent layers
enforce this (`eldercare.governor`'s `:actuation/finalize-care-plan`/
`:actuation/finalize-incident-response` high-stakes gate and
`eldercare.phase`'s phase table, which never puts `:care-plan/
finalize`/`:incident-response/finalize` in any phase's `:auto` set) --
see `eldercare.phase`'s docstring and `test/eldercare/phase_test.clj`'s
`care-plan-finalize-never-auto-at-any-phase`/`incident-response-
finalize-never-auto-at-any-phase`. The actor may draft, check and
recommend; a human facility administrator/care manager is always the
one who actually finalizes a care plan or incident response. Like
`6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`,
this actor has TWO actuation events.

## The core contract

```
resident intake + jurisdiction facts (eldercare.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ Eldercare-   │ ─────────────▶ │ Eldercare                    │  (independent system)
   │ Ops-LLM      │  + citations    │ Governor: spec-basis ·      │
   │ (sealed)     │                 │ evidence-incomplete ·        │
   └──────────────┘         commit ◀────┼──────────▶ hold │ care-plan-review-
                                 │             │           │ overdue (MAXIMUM-
                           record + ledger  escalate ─▶ human   ceiling temporal) ·
                                             (ALWAYS for         incident-flag-unresolved
                                              :care-plan/          (unconditional) ·
                                              finalize /            already-finalized
                                              :incident-response/
                                              finalize)
```

**The EldercareOps-LLM never finalizes a care plan or incident
response the Eldercare Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated jurisdiction
requirements; unsupported resident evidence; a care-plan review that
has exceeded its own regulatory ceiling; an unresolved incident flag; a
double finalization) force **hold** and *cannot* be approved past; a
clean finalization proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk two clean lifecycles (care-plan finalization, incident-response finalization) + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a mobility-assist and fall-
detection robot supports physical resident safety, under the actor,
gated by the independent **Eldercare Governor**. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions require
human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Eldercare Governor, care-plan-finalization + incident-response-finalization draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8730`). Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/
`9321`, this vertical's resident records are practice-specific rather
than a shared cross-operator data contract, so `eldercare.*` runs on
the generic identity/forms/dmn/bpmn/audit-ledger stack only -- no
bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/eldercare/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate care-plan-finalization/incident-response-finalization history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded resident, and the double-finalization guards check dedicated `:care-plan-finalized?`/`:incident-response-finalized?` booleans rather than a `:status` value |
| `src/eldercare/registry.cljc` | Care-plan-finalization + incident-response-finalization draft records, plus `care-plan-review-overdue?`/`max-review-interval-days` -- the FIRST check in this fleet's temporal-sufficiency family to enforce a MAXIMUM elapsed-time ceiling ("not too much time may pass") rather than a MINIMUM required wait |
| `src/eldercare/facts.cljc` | Per-jurisdiction assisted-living/eldercare catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/eldercare/eldercareopsllm.cljc` | **EldercareOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/incident-screening/care-plan-finalization/incident-response-finalization proposals |
| `src/eldercare/governor.cljc` | **Eldercare Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · care-plan-review-overdue, pure ground-truth MAXIMUM-ceiling recompute · incident-flag-unresolved, unconditional evaluation, the TENTH grounding of this discipline) + already-care-plan-finalized/already-incident-response-finalized guards + 1 soft (confidence/actuation gate) |
| `src/eldercare/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (both finalizations always human; resident intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/eldercare/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/eldercare/sim.cljc` | demo driver |
| `test/eldercare/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers resident intake through jurisdiction assessment,
incident screening, care-plan finalization and incident-response
finalization -- the core governed lifecycle this blueprint's own
`docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Resident intake + per-jurisdiction assisted-living checklisting, HARD-gated on an official spec-basis citation (`:resident/intake`/`:jurisdiction/assess`) | A full care-management/staffing-acuity program (clinical-acuity scoring, staffing-ratio engine, medication-management workflow -- see `care-plan-review-overdue?`'s docstring) |
| Incident screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:incident/screen`) | Real facility-management-system integration, family/guardian communication workflows |
| Care-plan finalization, HARD-gated on the resident's own review interval not having exceeded the regulatory ceiling and a double-finalization guard (`:care-plan/finalize`) | Ongoing day-to-day care-delivery workflows themselves |
| Incident-response finalization, HARD-gated on the resident's incident flag being resolved and a double-finalization guard (`:incident-response/finalize`) | |
| Immutable audit ledger for every intake/assessment/screening/finalization decision | |

Extending coverage is additive: add the next gate (e.g. a medication-
reconciliation check) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern
this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`eldercare.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `eldercare.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `eldercare.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `EldercareOps-LLM` + `Eldercare Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the twenty
prior actors' architecture. See `docs/adr/0001-architecture.md` for
the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.

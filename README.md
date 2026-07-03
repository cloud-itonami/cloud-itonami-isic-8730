# cloud-itonami-8730

Open Business Blueprint for **ISIC Rev.5 8730**: Residential care activities for the elderly and disabled.

This repository designs a forkable OSS business for residential care activities for the elderly and disabled -- assisted living and supportive housing without the level of nursing care in residential nursing facilities -- run by a qualified, licensed operator so a community or
independent provider never surrenders patient/resident data and ledgers to a
closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a mobility-assist and fall-detection robot supports physical resident safety,
under an actor that proposes actions and an independent **Eldercare Governor**
that gates them. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Core Contract

```text
intake + identity + care records
        |
        v
EldercareOps-LLM -> Eldercare Governor -> hold, proceed, or human approval
        |
        v
care ledger + evidence record + audit
```

No automated proposal, by itself, can complete the following without governor
approval and audit evidence: finalizing a care plan or incident response.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8730`). This vertical's care/case records are practice-specific rather
than a shared cross-operator data contract, so it runs on the generic
identity/forms/dmn/bpmn/audit-ledger stack -- no bespoke domain capability lib.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Maturity

`:blueprint` -- this repository is the published business/operator design.
The governed actor implementation (`EldercareOps-LLM` + `Eldercare Governor` as
running code) is a follow-up, same as any other `:blueprint`-tier
`cloud-itonami-*` entry in `kotoba-lang/industry`'s registry.

## License

Code and implementation templates are AGPL-3.0-or-later.

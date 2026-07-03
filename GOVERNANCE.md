# Governance

`cloud-itonami-8730` is an OSS open-business blueprint for residential care activities for the elderly and disabled -- assisted living and supportive housing without the level of nursing care in residential nursing facilities.
Governance covers both the capability layer and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the Eldercare Governor remains independent of the advisor.
- hard policy violations (fabricated assessment, incomplete records) cannot be
  overridden by human approval.
- finalizing a care plan or incident response always escalates to a human -- never automated.
- every hold, approval and care-action path is auditable.
- patient/resident and client data stay outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit and data-flow review.

Certified operators can lose certification for:

- bypassing the Eldercare Governor's policy checks
- mishandling patient/resident/client data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation

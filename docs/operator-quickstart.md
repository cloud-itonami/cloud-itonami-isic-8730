# Operator Quickstart: cloud-itonami-isic-8730

This guide covers the fastest path to validate and run the Eldercare Governor blueprint.

## Prerequisites

- **Clojure 1.12+** with the CLI tools (`clojure` command)
- **Java 17+** (for the JVM runtime)
- This repository cloned and the current directory set to its root

### Monorepo mode (workspace dev)

If you cloned this as part of the `cloud-itonami` monorepo workspace:

```bash
cd /path/to/cloud-itonami
cd orgs/cloud-itonami/cloud-itonami-isic-8730
```

The `deps.edn` in this repository references sibling dependencies (`langgraph-clj`, `langchain-clj`) via `:local/root` paths. These resolve automatically in the full workspace.

### Standalone fork

To fork this repository outside the monorepo:

1. Update `deps.edn` to replace `:local/root` references with published git coordinates:
   ```clj
   io.github.com-junkawasaki/langgraph-clj
   {:git/url "https://github.com/com-junkawasaki/langgraph.git" :git/tag "v0.1.0"}
   ```

2. Install dependencies: `clojure -P` (optional; runs automatically on first use)

## Run Tests

Validate the Eldercare Governor contract, phase invariants, and jurisdiction facts:

```bash
clojure -M:dev:test
```

This runs:
- Governor spec-basis, evidence completeness, and double-finalization guards
- Phase invariants (finalization is never auto, only human-approved)
- Store parity and registry conformance
- Jurisdiction facts coverage

Expected output: All tests pass. See `test/eldercare/*_test.clj` for details.

## Run Demo

Walk through two clean resident lifecycles (care-plan finalization + incident-response finalization) and five HARD-hold cases:

```bash
clojure -M:dev:run
```

Expected output: Demo traces show resident intake, jurisdiction assessment, care-plan proposal + governor approval/hold, and incident-response finalization paths with audit ledger entries.

## Lint

Static analysis via clj-kondo (errors fail):

```bash
clojure -M:lint
```

## Where the Governor sits

The **Eldercare Governor** — the independent verification layer that enforces HARD holds and checks — is in:

```
src/eldercare/governor.cljc
```

Key contract:
- `:actuation/finalize-care-plan` — verifies spec-basis, evidence completeness, review interval not overdue
- `:actuation/finalize-incident-response` — verifies incident flag is resolved, not double-finalized
- Both operations route to human approval (phase invariant: no auto finalization at any phase)

See `docs/adr/0001-architecture.md` for the full architecture.

## Next Steps

1. **Validate locally**: Run `clojure -M:dev:test` to confirm the contract holds
2. **Read the operator guide**: See `docs/operator-guide.md` for deployment and certification steps
3. **Review the business model**: See `docs/business-model.md` for revenue and customer segments
4. **Inspect the demo**: Run `clojure -M:dev:run` to see residents flowing through intake → assessment → finalization
5. **Fork for production**: Replace `:local/root` dependencies, configure your jurisdiction facts, deploy to your infrastructure

## Support & License

AGPL-3.0-or-later. See LICENSE for details.

For production deployment and SLA support, visit https://itonami.cloud.

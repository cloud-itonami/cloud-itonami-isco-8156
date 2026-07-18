# cloud-itonami-isco-8156

Open Occupation Blueprint for **ISCO-08 8156**: Shoemaking and Related
Machine Operators.

This repository designs a forkable OSS business for a footwear
manufacturing plant scheduling and logistics coordination practice: a
plant scheduling and supply-coordination robot manages crew/task
records under a governor-gated actor, so a shoemaking machine operator
crew keeps its own operating records instead of renting a closed
workforce-management SaaS.

**Maturity: `:implemented`.** `src/shoecoord/` implements the
`ShoeCoordActor` as a `langgraph.graph/state-graph` (`shoecoord.actor`)
wired to a `Shoemaking and Related Machine Operators Plant Scheduling
Coordination Advisor` (`shoecoord.advisor`) and an independent
`ShoeCoordGovernor` (`shoecoord.governor`), following the itonami
actor pattern (ADR-2607121000): `:intake -> :advise -> :govern ->
:decide -+-> :commit (:ok? true) +-> :request-approval (:escalate?
true, human-in-the-loop interrupt) +-> :hold (:hard? true)`. HARD
invariants (always hold, never overridable): operator provenance,
facility provenance, no-actuation (`:effect` must be `:propose`), a
closed op-allowlist (`:log-work-record`, `:schedule-crew-operation`,
`:flag-safety-concern`, `:coordinate-supply-order` — nothing else may
ever be proposed), and a permanent, unconditional block on any
proposal that would directly finalize a machine-operation-execution
decision (e.g. deciding to proceed with a specific
pressing/cutting/stitching run) or a plant-safety-clearance decision
(e.g. declaring the plant safety cleared), or that would override a
plant safety officer's judgment. Always-escalate paths (human sign-off
regardless of confidence, mapping this repo's Trust Controls in
[`docs/business-model.md`](docs/business-model.md)):
`:flag-safety-concern` (always) and `:coordinate-supply-order` above
the registered cost threshold.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a plant scheduling/logistics coordination
robot performs crew scheduling, production-run/inventory/progress-record
logging and leather/sole/adhesive-materials supply-order coordination for a
shoemaking and related machine operator crew, under an actor that proposes
actions and an independent **Shoemaking and Related Machine Operators
Plant Scheduling Coordination Governor** that gates them. The governor
never dispatches hardware itself, never operates shoemaking machinery
on the plant floor, and never finalizes a machine-operation-execution
decision or a plant-safety-clearance decision, and never overrides a
plant safety officer's judgment; `:high`/`:safety-critical` actions
(such as a flagged press-crush/cutting-die-injury/
adhesive-fume-exposure/equipment-condition concern, or an
above-threshold supply order) require human sign-off. **This actor
coordinates PLANT SCHEDULING/LOGISTICS ONLY — it never operates
shoemaking machinery itself, and it never makes a
plant-safety-clearance decision itself.**

Shoemaking and Related Machine Operators run cutting/stitching/molding
machinery for footwear manufacture (cutting-die presses, stitching
machines, sole-molding/lasting machines) — a real heavy-machinery
hazard domain (crush and cutting-blade injury from presses and cutting
dies) plus adhesive-fume exposure. This actor never operates that
equipment and never clears it as safe — it only schedules and logs
around it, and always routes machinery-hazard/adhesive-fume-exposure
concerns to a human plant safety officer.

## Core Contract

```text
crew roster + facility registration + safety-reporting policy
        |
        v
Shoemaking and Related Machine Operators Plant Scheduling Coordination
Advisor -> ShoeCoordGovernor -> log/schedule/coordinate, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses,
finalize a machine-operation-execution decision, finalize a
plant-safety-clearance decision, override a plant safety officer's
judgment, suppress an operating record, or disclose sensitive data without
governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8156`). Required capabilities:

- :robotics
- :identity
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.

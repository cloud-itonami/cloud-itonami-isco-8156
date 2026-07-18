(ns shoecoord.store
  "SSoT for the ISCO-08 8156 shoemaking and related machine operators
  plant scheduling/logistics coordination actor (itonami actor pattern,
  ADR-2607121000 / CLAUDE.md Actors section; README's 'Robotics
  premise' — a plant scheduling/logistics coordination robot performs
  crew scheduling, production-run/inventory/progress-record logging
  and leather/sole/adhesive-materials supply-order coordination for a
  shoemaking machine operator crew under this advisor/governor pair,
  which never dispatches hardware itself, never operates shoemaking
  machinery itself, and never finalizes a machine-operation-execution
  decision or a plant-safety-clearance decision, and never overrides a
  plant safety officer's judgment — those remain the plant safety
  officer's exclusive judgment). Modeled closely on
  cloud-itonami-isco-8143's papercoord.store.

  Domain:

    operator  — a registered shoemaking and related machine operator
                crew member who runs cutting/stitching/molding
                shoemaking equipment (cutting-die presses, stitching
                machines, sole-molding/lasting machines)
                (:operator-id, :name)
    facility  — a registered footwear manufacturing facility/line
                {:facility-id :name :max-supply-cost number}.
                `:max-supply-cost` is an informational registered
                ceiling used only to decide whether a
                `:coordinate-supply-order` proposal escalates to human
                sign-off (the governor never blocks a
                within-threshold order outright; it only decides
                commit vs. escalate).
    record    — a committed operating record (a logged production-run/
                inventory/progress entry, a scheduled crew/shift
                operation, a flagged safety concern, or a coordinated
                leather/sole/adhesive-materials supply order) —
                written ONLY via commit-record!. This actor
                coordinates plant scheduling/logistics ONLY — a
                `record` is a coordination artifact, never a
                machine-operation-execution act, never a
                plant-safety-clearance decision, and never a plant
                safety officer's-judgment override.
    ledger    — append-only audit trail, commit or hold.")

(defprotocol Store
  (operator [s operator-id])
  (facility [s facility-id])
  (records-of [s operator-id])
  (ledger [s])
  (register-operator! [s operator])
  (register-facility! [s facility])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (operator [_ operator-id] (get-in @a [:operators operator-id]))
  (facility [_ facility-id] (get-in @a [:facilities facility-id]))
  (records-of [_ operator-id] (filter #(= operator-id (:operator-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-operator! [s o]
    (swap! a assoc-in [:operators (:operator-id o)] o) s)
  (register-facility! [s f]
    (swap! a assoc-in [:facilities (:facility-id f)] f) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:operators {} :facilities {} :records [] :ledger []}
                                    seed)))))

(ns shoecoord.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [shoecoord.store :as store]
            [shoecoord.advisor :as advisor]
            [shoecoord.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-operator! st {:operator-id "operator-1" :name "Aki Sato"})
    (store/register-facility! st {:facility-id "F-1" :name "Kobo Footwear Plant" :max-supply-cost 2000})
    st))

(defn- op [op-kw & {:as extra}]
  (merge {:op op-kw :effect :propose :facility-id "F-1"
          :confidence 0.9 :stake :low}
         extra))

(def ^:private req {:operator-id "operator-1"})

(deftest ok-log-work-record
  (let [st (fresh-store)
        v (governor/check req {} (op :log-work-record) st)]
    (is (:ok? v))))

(deftest ok-schedule-crew-operation
  (let [st (fresh-store)
        v (governor/check req {} (op :schedule-crew-operation) st)]
    (is (:ok? v))))

(deftest ok-supply-order-at-threshold-boundary
  (testing "the supply-cost threshold escalate boundary is exclusive (over, not at)"
    (let [st (fresh-store)
          v (governor/check req {} (op :coordinate-supply-order :cost 2000) st)]
      (is (:ok? v)))))

(deftest hard-on-unregistered-operator
  (let [st (fresh-store)
        v (governor/check {:operator-id "nobody"} {} (op :log-work-record) st)]
    (is (:hard? v))
    (is (some #(= :no-operator (:rule %)) (:violations v)))))

(deftest hard-on-unregistered-facility
  (let [st (fresh-store)
        v (governor/check req {} (op :log-work-record :facility-id "F-ghost") st)]
    (is (:hard? v))
    (is (some #(= :no-facility (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-work-record) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest hard-on-op-outside-closed-allowlist
  (let [st (fresh-store)
        v (governor/check req {} (op :dispatch-equipment) st)]
    (is (:hard? v))
    (is (some #(= :unknown-op (:rule %)) (:violations v)))))

(deftest hard-on-scope-excluded-op-finalize-shoemaking-machine-operation
  (testing "finalizing a machine-operation-execution decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-shoemaking-machine-operation) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-finalize-machine-operation-decision
  (testing "finalizing the machine operation decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-machine-operation-decision) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-authorize-shoemaking-machine-run
  (testing "authorizing a shoemaking machine run is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :authorize-shoemaking-machine-run) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-proceed-with-shoemaking-machine-run
  (testing "proceeding with a shoemaking machine run is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :proceed-with-shoemaking-machine-run) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-finalize-plant-safety-clearance
  (testing "finalizing a plant-safety-clearance decision is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :finalize-plant-safety-clearance) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-declare-plant-safety-cleared
  (testing "declaring the plant safety cleared is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :declare-plant-safety-cleared) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-declare-plant-safety-clearance
  (testing "declaring the plant safety clearance (alternate op name) is a permanent block, never a routine op"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :declare-plant-safety-clearance) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-op-override-plant-safety-officer-judgment
  (testing "overriding a plant safety officer's judgment is a plant safety officer's exclusive judgment, never this actor's"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :override-plant-safety-officer-judgment) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-pressing-run
  (testing "defense-in-depth: a rationale that itself attempts to proceed with the pressing run is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :schedule-crew-operation)
                                           :rationale "recommend we proceed with the pressing run now")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-plant-safety-clearance
  (testing "defense-in-depth: a rationale that itself attempts to declare the plant safety cleared is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :log-work-record)
                                           :rationale "we can declare the plant safety cleared now")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-finalize-shoemaking-machine-operation
  (testing "defense-in-depth: a rationale that itself attempts to finalize the shoemaking-machine operation is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :coordinate-supply-order :cost 100)
                                           :rationale "recommend we finalize the shoemaking-machine operation today")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-override-plant-safety-officer
  (testing "defense-in-depth: a rationale attempting to override the plant safety officer's judgment is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :log-work-record)
                                           :rationale "override the plant safety officer's judgment and proceed")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest hard-on-scope-excluded-rationale-declare-plant-safety-clearance
  (testing "defense-in-depth: a rationale that itself attempts to finalize the plant-safety clearance is blocked even if the op looks routine"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :log-work-record)
                                           :rationale "we should finalize the plant-safety clearance now")
                             st)]
      (is (:hard? v))
      (is (some #(= :scope-excluded-action (:rule %)) (:violations v))))))

(deftest always-escalates-safety-concern-even-at-high-confidence
  (testing "a press-crush/cutting-die-injury/adhesive-fume-exposure/equipment-condition concern always requires human sign-off"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (op :flag-safety-concern :hazard-type :press-crush)
                                           :confidence 0.99)
                             st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-supply-order-above-threshold
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :coordinate-supply-order :cost 5000) :confidence 0.99) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (op :log-work-record) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest default-mock-advisor-proposals-never-self-trip-on-scope-exclusion
  (testing "the governor's scope-exclusion term list must never match the mock advisor's own default rationale text for any allowlisted op — CLAUDE.md's known self-tripping bug pattern (rationale legitimately contains bare nouns like 'shoe'/'leather'/'adhesive'/'cutting die', but never the full finalization-action phrases)"
    (let [st (fresh-store)
          adv (advisor/mock-advisor)
          ops [:log-work-record :schedule-crew-operation
               :flag-safety-concern :coordinate-supply-order]]
      (doseq [o ops]
        (let [request {:operator-id "operator-1" :op o :facility-id "F-1"
                        :stake :low :task "routine shoemaking task with leather uppers and cutting dies"
                        :hazard-type :press-crush :cost 500}
              proposal (advisor/-advise adv st request)
              v (governor/check request {} proposal st)]
          (is (not (:hard? v))
              (str o " proposal unexpectedly hard-blocked: " (:violations v))))))))

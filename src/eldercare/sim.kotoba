(ns eldercare.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean resident through
  intake -> jurisdiction assessment -> incident screening -> care-plan-
  finalization proposal (always escalates) -> human approval -> commit,
  then through incident-response-finalization proposal (always
  escalates) -> human approval -> commit, then shows five HARD holds (a
  jurisdiction with no spec-basis, an overdue care-plan review, an
  unresolved incident flag screened directly via `:incident/screen`
  [never via an actuation op against an unscreened resident -- see
  ADR-2607071922 Decision 5 / this actor's own governor ns docstring],
  and a double care-plan/incident-response finalization of an already-
  processed resident) that never reach a human at all, and prints the
  audit ledger + the draft care-plan-finalization and incident-
  response-finalization records."
  (:require [langgraph.graph :as g]
            [eldercare.store :as store]
            [eldercare.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :care-manager :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== resident/intake resident-1 (JPN, clean; days-since-review 30, incident-flag resolved) ==")
    (println (exec! actor "t1" {:op :resident/intake :subject "resident-1"
                                :patch {:id "resident-1" :resident-name "Sakura Tanaka"}} operator))

    (println "== jurisdiction/assess resident-1 (escalates -- human approves) ==")
    (println (exec! actor "t2" {:op :jurisdiction/assess :subject "resident-1"} operator))
    (println (approve! actor "t2"))

    (println "== incident/screen resident-1 (clean; escalates -- human approves) ==")
    (println (exec! actor "t3" {:op :incident/screen :subject "resident-1"} operator))
    (println (approve! actor "t3"))

    (println "== care-plan/finalize resident-1 (always escalates -- actuation/finalize-care-plan) ==")
    (let [r (exec! actor "t4" {:op :care-plan/finalize :subject "resident-1"} operator)]
      (println r)
      (println "-- human care manager approves --")
      (println (approve! actor "t4")))

    (println "== incident-response/finalize resident-1 (always escalates -- actuation/finalize-incident-response) ==")
    (let [r (exec! actor "t5" {:op :incident-response/finalize :subject "resident-1"} operator)]
      (println r)
      (println "-- human care manager approves --")
      (println (approve! actor "t5")))

    (println "== jurisdiction/assess resident-2 (no spec-basis -> HARD hold) ==")
    (println (exec! actor "t6" {:op :jurisdiction/assess :subject "resident-2" :no-spec? true} operator))

    (println "== jurisdiction/assess resident-3 (escalates -- human approves; sets up the review-overdue test) ==")
    (println (exec! actor "t7" {:op :jurisdiction/assess :subject "resident-3"} operator))
    (println (approve! actor "t7"))

    (println "== care-plan/finalize resident-3 (days-since-review 120 > 90 -> HARD hold) ==")
    (println (exec! actor "t8" {:op :care-plan/finalize :subject "resident-3"} operator))

    (println "== incident/screen resident-4 (unresolved incident flag -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t9" {:op :incident/screen :subject "resident-4"} operator))

    (println "== care-plan/finalize resident-1 AGAIN (double-finalization -> HARD hold) ==")
    (println (exec! actor "t10" {:op :care-plan/finalize :subject "resident-1"} operator))

    (println "== incident-response/finalize resident-1 AGAIN (double-finalization -> HARD hold) ==")
    (println (exec! actor "t11" {:op :incident-response/finalize :subject "resident-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft care-plan-finalization records ==")
    (doseq [r (store/care-plan-history db)] (println r))

    (println "== draft incident-response-finalization records ==")
    (doseq [r (store/incident-response-history db)] (println r))))

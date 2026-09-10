(ns eldercare.governor-contract-test
  "The governor contract as executable tests -- the residential-
  eldercare analog of `cloud-itonami-isic-6512`'s `casualty.governor-
  contract-test`. The single invariant under test:

    EldercareOps-LLM never finalizes a care plan or incident response
    the Eldercare Governor would reject, `:care-plan/finalize`/
    `:incident-response/finalize` NEVER auto-commit at any phase,
    `:resident/intake` (no direct capital risk) MAY auto-commit when
    clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [eldercare.store :as store]
            [eldercare.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :care-manager :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- screen!
  "Walks `subject` through incident screening -> approve, leaving a
  screening on file. Only safe to call for a resident whose incident
  flag is already resolved -- an unresolved flag HARD-holds the screen
  itself (see `incident-flag-unresolved-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :incident/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :resident/intake :subject "resident-1"
                   :patch {:id "resident-1" :resident-name "Sakura Tanaka"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Tanaka" (:resident-name (store/resident db "resident-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "resident-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "resident-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "resident-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "resident-1")) "no assessment written"))))

(deftest care-plan-finalize-without-assessment-is-held
  (testing "care-plan/finalize before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :care-plan/finalize :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest care-plan-review-overdue-is-held
  (testing "a resident whose days-since-last-care-plan-review exceeds the max-review-interval-days ceiling -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "resident-3")
          res (exec-op actor "t5" {:op :care-plan/finalize :subject "resident-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:care-plan-review-overdue} (-> (store/ledger db) last :basis)))
      (is (empty? (store/care-plan-history db))))))

(deftest incident-flag-unresolved-is-held-and-unoverridable
  (testing "an unresolved incident flag on a resident -> HOLD, and never reaches request-approval -- exercised via :incident/screen DIRECTLY, not via an actuation op against an unscreened resident (see this actor's governor ns docstring / ADR-2607071922 Decision 5)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :incident/screen :subject "resident-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:incident-flag-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/incident-screening-of db "resident-4")) "no clearance written"))))

(deftest care-plan-finalize-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, review-current, incident-clear resident still ALWAYS interrupts for human approval -- actuation/finalize-care-plan is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "resident-1")
          _ (screen! actor "t7pre2" "resident-1")
          r1 (exec-op actor "t7" {:op :care-plan/finalize :subject "resident-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, plan record drafted"
        (let [r2 (approve! actor "t7")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:care-plan-finalized? (store/resident db "resident-1"))))
          (is (= 1 (count (store/care-plan-history db))) "one draft plan record"))))))

(deftest incident-response-finalize-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, incident-clear resident still ALWAYS interrupts for human approval -- actuation/finalize-incident-response is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "resident-1")
          _ (screen! actor "t8pre2" "resident-1")
          r1 (exec-op actor "t8" {:op :incident-response/finalize :subject "resident-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, response record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:incident-response-finalized? (store/resident db "resident-1"))))
          (is (= 1 (count (store/incident-response-history db))) "one draft response record"))))))

(deftest care-plan-finalize-double-finalization-is-held
  (testing "finalizing the same resident's care plan twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "resident-1")
          _ (screen! actor "t9pre2" "resident-1")
          _ (exec-op actor "t9a" {:op :care-plan/finalize :subject "resident-1"} operator)
          _ (approve! actor "t9a")
          res (exec-op actor "t9" {:op :care-plan/finalize :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-care-plan-finalized} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/care-plan-history db))) "still only the one earlier finalization"))))

(deftest incident-response-finalize-double-finalization-is-held
  (testing "finalizing the same resident's incident response twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "resident-1")
          _ (screen! actor "t10pre2" "resident-1")
          _ (exec-op actor "t10a" {:op :incident-response/finalize :subject "resident-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :incident-response/finalize :subject "resident-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-incident-response-finalized} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/incident-response-history db))) "still only the one earlier finalization"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :resident/intake :subject "resident-1"
                          :patch {:id "resident-1" :resident-name "Sakura Tanaka"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "resident-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))

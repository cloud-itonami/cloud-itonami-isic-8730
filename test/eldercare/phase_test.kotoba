(ns eldercare.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:care-plan/finalize`/`:incident-response/finalize` must
  NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [eldercare.phase :as phase]))

(deftest care-plan-finalize-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real care-plan finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :care-plan/finalize))
          (str "phase " n " must not auto-commit :care-plan/finalize")))))

(deftest incident-response-finalize-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-finalizes a real incident response"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :incident-response/finalize))
          (str "phase " n " must not auto-commit :incident-response/finalize")))))

(deftest incident-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling KYC/conflict/independence/surveillance/calibration/credential/integrity/patron/authorization/safety-test screen"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :incident/screen))
          (str "phase " n " must not auto-commit :incident/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":resident/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:resident/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :resident/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :care-plan/finalize} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :incident-response/finalize} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :resident/intake} :commit)))))

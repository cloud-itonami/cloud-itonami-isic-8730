(ns eldercare.facts-test
  (:require [clojure.test :refer [deftest is]]
            [eldercare.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest aus-has-a-spec-basis-with-the-same-shape-as-existing-entries
  (let [aus (facts/spec-basis "AUS")]
    (is (some? aus))
    (is (= "Australia" (:name aus)))
    (is (string? (:owner-authority aus)))
    (is (string? (:legal-basis aus)))
    (is (string? (:national-spec aus)))
    (is (string? (:provenance aus)))
    (is (= 4 (count (:required-evidence aus))))
    (is (every? string? (:required-evidence aus)))
    ;; genuinely a federal/Commonwealth matter -- verify honestly rather
    ;; than assuming symmetry with state-based regimes (unlike DEU/USA
    ;; above, which are federated and cite one representative state).
    (is (re-find #"\(Cth\)" (:legal-basis aus)))
    (is (re-find #"Aged Care Quality and Safety Commission" (:owner-authority aus)))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(ns eldercare.registry-test
  (:require [clojure.test :refer [deftest is]]
            [eldercare.registry :as r]))

;; ----------------------------- care-plan-review-overdue? -----------------------------

(deftest review-not-overdue-when-within-interval
  (is (not (r/care-plan-review-overdue? {:days-since-last-care-plan-review 30})))
  (is (not (r/care-plan-review-overdue? {:days-since-last-care-plan-review 90}))
      "exactly at the ceiling is not yet overdue"))

(deftest review-overdue-when-exceeding-interval
  (is (r/care-plan-review-overdue? {:days-since-last-care-plan-review 91}))
  (is (r/care-plan-review-overdue? {:days-since-last-care-plan-review 120})))

(deftest review-overdue-is-false-on-missing-or-non-numeric-field
  (is (not (r/care-plan-review-overdue? {})))
  (is (not (r/care-plan-review-overdue? {:days-since-last-care-plan-review nil})))
  (is (not (r/care-plan-review-overdue? {:days-since-last-care-plan-review "120"}))))

;; ----------------------------- register-care-plan-finalization -----------------------------

(deftest care-plan-finalization-is-a-draft-not-a-real-finalization
  (let [result (r/register-care-plan-finalization "resident-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest care-plan-finalization-assigns-plan-number
  (let [result (r/register-care-plan-finalization "resident-1" "JPN" 7)]
    (is (= (get result "plan_number") "JPN-CPL-000007"))
    (is (= (get-in result ["record" "resident_id"]) "resident-1"))
    (is (= (get-in result ["record" "kind"]) "care-plan-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest care-plan-finalization-validation-rules
  (is (thrown? Exception (r/register-care-plan-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-care-plan-finalization "resident-1" "" 0)))
  (is (thrown? Exception (r/register-care-plan-finalization "resident-1" "JPN" -1))))

(deftest care-plan-history-is-append-only
  (let [c1 (r/register-care-plan-finalization "resident-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-care-plan-finalization "resident-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-CPL-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-CPL-000001" (get-in hist2 [1 "record_id"])))))

;; ----------------------------- register-incident-response-finalization -----------------------------

(deftest incident-response-finalization-is-a-draft-not-a-real-finalization
  (let [result (r/register-incident-response-finalization "resident-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest incident-response-finalization-assigns-response-number
  (let [result (r/register-incident-response-finalization "resident-1" "JPN" 7)]
    (is (= (get result "response_number") "JPN-INC-000007"))
    (is (= (get-in result ["record" "resident_id"]) "resident-1"))
    (is (= (get-in result ["record" "kind"]) "incident-response-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest incident-response-finalization-validation-rules
  (is (thrown? Exception (r/register-incident-response-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-incident-response-finalization "resident-1" "" 0)))
  (is (thrown? Exception (r/register-incident-response-finalization "resident-1" "JPN" -1))))

(deftest incident-response-history-is-append-only
  (let [d1 (r/register-incident-response-finalization "resident-1" "JPN" 0)
        hist (r/append [] d1)
        d2 (r/register-incident-response-finalization "resident-2" "JPN" 1)
        hist2 (r/append hist d2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-INC-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-INC-000001" (get-in hist2 [1 "record_id"])))))

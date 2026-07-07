(ns eldercare.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [eldercare.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka" (:resident-name (store/resident s "resident-1"))))
      (is (= "JPN" (:jurisdiction (store/resident s "resident-1"))))
      (is (= 30 (:days-since-last-care-plan-review (store/resident s "resident-1"))))
      (is (true? (:incident-flag-resolved? (store/resident s "resident-1"))))
      (is (= 120 (:days-since-last-care-plan-review (store/resident s "resident-3"))))
      (is (false? (:incident-flag-resolved? (store/resident s "resident-4"))))
      (is (false? (:care-plan-finalized? (store/resident s "resident-1"))))
      (is (false? (:incident-response-finalized? (store/resident s "resident-1"))))
      (is (= ["resident-1" "resident-2" "resident-3" "resident-4"]
             (mapv :id (store/all-residents s))))
      (is (nil? (store/incident-screening-of s "resident-1")))
      (is (nil? (store/assessment-of s "resident-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/care-plan-history s)))
      (is (= [] (store/incident-response-history s)))
      (is (zero? (store/next-care-plan-sequence s "JPN")))
      (is (zero? (store/next-incident-response-sequence s "JPN")))
      (is (false? (store/resident-care-plan-already-finalized? s "resident-1")))
      (is (false? (store/resident-incident-response-already-finalized? s "resident-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :resident/upsert
                                 :value {:id "resident-1" :resident-name "Sakura Tanaka"}})
        (is (= "Sakura Tanaka" (:resident-name (store/resident s "resident-1"))))
        (is (= 30 (:days-since-last-care-plan-review (store/resident s "resident-1"))) "unrelated field preserved"))
      (testing "assessment / incident-screening payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["resident-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "resident-1")))
        (store/commit-record! s {:effect :incident-screening/set :path ["resident-1"]
                                 :payload {:resident-id "resident-1" :verdict :resolved}})
        (is (= {:resident-id "resident-1" :verdict :resolved} (store/incident-screening-of s "resident-1"))))
      (testing "care-plan finalization drafts a plan record and advances the care-plan sequence"
        (store/commit-record! s {:effect :resident/mark-care-plan-finalized :path ["resident-1"]})
        (is (= "JPN-CPL-000000" (get (first (store/care-plan-history s)) "record_id")))
        (is (= "care-plan-finalization-draft" (get (first (store/care-plan-history s)) "kind")))
        (is (true? (:care-plan-finalized? (store/resident s "resident-1"))))
        (is (= 1 (count (store/care-plan-history s))))
        (is (= 1 (store/next-care-plan-sequence s "JPN")))
        (is (true? (store/resident-care-plan-already-finalized? s "resident-1")))
        (is (false? (store/resident-care-plan-already-finalized? s "resident-2"))))
      (testing "incident-response finalization drafts a response record and advances the incident-response sequence"
        (store/commit-record! s {:effect :resident/mark-incident-response-finalized :path ["resident-1"]})
        (is (= "JPN-INC-000000" (get (first (store/incident-response-history s)) "record_id")))
        (is (= "incident-response-finalization-draft" (get (first (store/incident-response-history s)) "kind")))
        (is (true? (:incident-response-finalized? (store/resident s "resident-1"))))
        (is (= 1 (count (store/incident-response-history s))))
        (is (= 1 (store/next-incident-response-sequence s "JPN")))
        (is (true? (store/resident-incident-response-already-finalized? s "resident-1")))
        (is (false? (store/resident-incident-response-already-finalized? s "resident-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/resident s "nope")))
    (is (= [] (store/all-residents s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/care-plan-history s)))
    (is (= [] (store/incident-response-history s)))
    (is (zero? (store/next-care-plan-sequence s "JPN")))
    (is (zero? (store/next-incident-response-sequence s "JPN")))
    (store/with-residents s {"x" {:id "x" :resident-name "n" :days-since-last-care-plan-review 1
                                  :incident-flag-resolved? true :care-plan-finalized? false
                                  :incident-response-finalized? false :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:resident-name (store/resident s "x"))))))

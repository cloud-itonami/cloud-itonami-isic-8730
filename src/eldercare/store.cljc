(ns eldercare.store
  "SSoT for the residential-eldercare actor, behind a `Store` protocol
  so the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/eldercare/store_contract_test.clj), which is the whole point:
  the actor, the Eldercare Governor and the audit ledger never know
  which SSoT they run on.

  Like `marketadmin.store`'s dual admission/halt-lift history,
  `registrar.store`'s dual grade/degree history, `wagering.store`'s
  dual acceptance/settlement history and `repairshop.store`'s dual
  completion/return history, this actor has TWO actuation events
  (care-plan finalization, incident-response finalization) acting on
  the SAME entity (a resident), each with its OWN history collection,
  sequence counter and dedicated double-actuation-guard boolean
  (`:care-plan-finalized?`/`:incident-response-finalized?`, never a
  `:status` value) -- the same discipline `accounting.governor`'s/
  `marketadmin.governor`'s/`testlab.governor`'s/`clinic.governor`'s/
  `registrar.governor`'s/`wagering.governor`'s/`veterinary.
  governor`'s/`funeral.governor`'s/`repairshop.governor`'s/
  `parksafety.governor`'s guards establish.

  The ledger stays append-only on every backend: 'which resident was
  screened for an unresolved incident flag, which care plan was
  finalized, which incident response was finalized, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a resident/family trusting a
  facility needs, and the evidence an operator needs if a care plan or
  an incident response is later disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [eldercare.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (resident [s id])
  (all-residents [s])
  (incident-screening-of [s resident-id] "committed incident screening verdict for a resident, or nil")
  (assessment-of [s resident-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (care-plan-history [s] "the append-only care-plan-finalization history (eldercare.registry drafts)")
  (incident-response-history [s] "the append-only incident-response-finalization history (eldercare.registry drafts)")
  (next-care-plan-sequence [s jurisdiction] "next care-plan-finalization-number sequence for a jurisdiction")
  (next-incident-response-sequence [s jurisdiction] "next incident-response-finalization-number sequence for a jurisdiction")
  (resident-care-plan-already-finalized? [s resident-id] "has this resident's care plan already been finalized?")
  (resident-incident-response-already-finalized? [s resident-id] "has this resident's incident response already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-residents [s residents] "replace/seed the resident directory (map id->resident)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained resident set covering both actuation
  lifecycles (care-plan finalization, incident-response finalization)
  so the actor + tests run offline."
  []
  {:residents
   {"resident-1" {:id "resident-1" :resident-name "Sakura Tanaka"
                   :days-since-last-care-plan-review 30 :incident-flag-resolved? true
                   :care-plan-finalized? false :incident-response-finalized? false
                   :jurisdiction "JPN" :status :intake}
    "resident-2" {:id "resident-2" :resident-name "Atlantis Doe"
                   :days-since-last-care-plan-review 30 :incident-flag-resolved? true
                   :care-plan-finalized? false :incident-response-finalized? false
                   :jurisdiction "ATL" :status :intake}
    "resident-3" {:id "resident-3" :resident-name "鈴木一郎"
                   :days-since-last-care-plan-review 120 :incident-flag-resolved? true
                   :care-plan-finalized? false :incident-response-finalized? false
                   :jurisdiction "JPN" :status :intake}
    "resident-4" {:id "resident-4" :resident-name "田中花子"
                   :days-since-last-care-plan-review 30 :incident-flag-resolved? false
                   :care-plan-finalized? false :incident-response-finalized? false
                   :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-care-plan!
  "Backend-agnostic `:resident/mark-care-plan-finalized` -- looks up
  the resident via the protocol and drafts the care-plan-finalization
  record, and returns {:result .. :resident-patch ..} for the caller
  to persist."
  [s resident-id]
  (let [r (resident s resident-id)
        seq-n (next-care-plan-sequence s (:jurisdiction r))
        result (registry/register-care-plan-finalization resident-id (:jurisdiction r) seq-n)]
    {:result result
     :resident-patch {:care-plan-finalized? true
                      :plan-number (get result "plan_number")}}))

(defn- finalize-incident-response!
  "Backend-agnostic `:resident/mark-incident-response-finalized` --
  looks up the resident via the protocol and drafts the incident-
  response-finalization record, and returns {:result .. :resident-
  patch ..} for the caller to persist."
  [s resident-id]
  (let [r (resident s resident-id)
        seq-n (next-incident-response-sequence s (:jurisdiction r))
        result (registry/register-incident-response-finalization resident-id (:jurisdiction r) seq-n)]
    {:result result
     :resident-patch {:incident-response-finalized? true
                      :response-number (get result "response_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (resident [_ id] (get-in @a [:residents id]))
  (all-residents [_] (sort-by :id (vals (:residents @a))))
  (incident-screening-of [_ id] (get-in @a [:incident-screenings id]))
  (assessment-of [_ resident-id] (get-in @a [:assessments resident-id]))
  (ledger [_] (:ledger @a))
  (care-plan-history [_] (:care-plans @a))
  (incident-response-history [_] (:incident-responses @a))
  (next-care-plan-sequence [_ jurisdiction] (get-in @a [:care-plan-sequences jurisdiction] 0))
  (next-incident-response-sequence [_ jurisdiction] (get-in @a [:incident-response-sequences jurisdiction] 0))
  (resident-care-plan-already-finalized? [_ resident-id] (boolean (get-in @a [:residents resident-id :care-plan-finalized?])))
  (resident-incident-response-already-finalized? [_ resident-id] (boolean (get-in @a [:residents resident-id :incident-response-finalized?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :resident/upsert
      (swap! a update-in [:residents (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :incident-screening/set
      (swap! a assoc-in [:incident-screenings (first path)] payload)

      :resident/mark-care-plan-finalized
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-care-plan! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:care-plan-sequences jurisdiction] (fnil inc 0))
                       (update-in [:residents resident-id] merge resident-patch)
                       (update :care-plans registry/append result))))
        result)

      :resident/mark-incident-response-finalized
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-incident-response! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:incident-response-sequences jurisdiction] (fnil inc 0))
                       (update-in [:residents resident-id] merge resident-patch)
                       (update :incident-responses registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-residents [s residents] (when (seq residents) (swap! a assoc :residents residents)) s))

(defn seed-db
  "A MemStore seeded with the demo resident set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :incident-screenings {} :ledger [] :care-plan-sequences {}
                           :care-plans [] :incident-response-sequences {} :incident-responses []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/incident-screening payloads, ledger
  facts, care-plan/incident-response records) are stored as EDN
  strings so `langchain.db` doesn't expand them into sub-entities --
  the same convention every sibling actor's store uses."
  {:resident/id                        {:db/unique :db.unique/identity}
   :assessment/resident-id              {:db/unique :db.unique/identity}
   :incident-screening/resident-id       {:db/unique :db.unique/identity}
   :ledger/seq                            {:db/unique :db.unique/identity}
   :care-plan/seq                          {:db/unique :db.unique/identity}
   :incident-response/seq                   {:db/unique :db.unique/identity}
   :care-plan-sequence/jurisdiction           {:db/unique :db.unique/identity}
   :incident-response-sequence/jurisdiction    {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- resident->tx [{:keys [id resident-name days-since-last-care-plan-review incident-flag-resolved?
                             care-plan-finalized? incident-response-finalized?
                             jurisdiction status plan-number response-number]}]
  (cond-> {:resident/id id}
    resident-name                        (assoc :resident/resident-name resident-name)
    days-since-last-care-plan-review       (assoc :resident/days-since-last-care-plan-review days-since-last-care-plan-review)
    (some? incident-flag-resolved?)          (assoc :resident/incident-flag-resolved? incident-flag-resolved?)
    (some? care-plan-finalized?)                (assoc :resident/care-plan-finalized? care-plan-finalized?)
    (some? incident-response-finalized?)           (assoc :resident/incident-response-finalized? incident-response-finalized?)
    jurisdiction                                     (assoc :resident/jurisdiction jurisdiction)
    status                                             (assoc :resident/status status)
    plan-number                                          (assoc :resident/plan-number plan-number)
    response-number                                        (assoc :resident/response-number response-number)))

(def ^:private resident-pull
  [:resident/id :resident/resident-name :resident/days-since-last-care-plan-review
   :resident/incident-flag-resolved? :resident/care-plan-finalized? :resident/incident-response-finalized?
   :resident/jurisdiction :resident/status :resident/plan-number :resident/response-number])

(defn- pull->resident [m]
  (when (:resident/id m)
    {:id (:resident/id m) :resident-name (:resident/resident-name m)
     :days-since-last-care-plan-review (:resident/days-since-last-care-plan-review m)
     :incident-flag-resolved? (boolean (:resident/incident-flag-resolved? m))
     :care-plan-finalized? (boolean (:resident/care-plan-finalized? m))
     :incident-response-finalized? (boolean (:resident/incident-response-finalized? m))
     :jurisdiction (:resident/jurisdiction m) :status (:resident/status m)
     :plan-number (:resident/plan-number m) :response-number (:resident/response-number m)}))

(defrecord DatomicStore [conn]
  Store
  (resident [_ id]
    (pull->resident (d/pull (d/db conn) resident-pull [:resident/id id])))
  (all-residents [_]
    (->> (d/q '[:find [?id ...] :where [?e :resident/id ?id]] (d/db conn))
         (map #(pull->resident (d/pull (d/db conn) resident-pull [:resident/id %])))
         (sort-by :id)))
  (incident-screening-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?rid
                :where [?k :incident-screening/resident-id ?rid] [?k :incident-screening/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ resident-id]
    (dec* (d/q '[:find ?p . :in $ ?rid
                :where [?a :assessment/resident-id ?rid] [?a :assessment/payload ?p]]
              (d/db conn) resident-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (care-plan-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :care-plan/seq ?s] [?e :care-plan/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (incident-response-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :incident-response/seq ?s] [?e :incident-response/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-care-plan-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :care-plan-sequence/jurisdiction ?j] [?e :care-plan-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-incident-response-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :incident-response-sequence/jurisdiction ?j] [?e :incident-response-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (resident-care-plan-already-finalized? [s resident-id]
    (boolean (:care-plan-finalized? (resident s resident-id))))
  (resident-incident-response-already-finalized? [s resident-id]
    (boolean (:incident-response-finalized? (resident s resident-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :resident/upsert
      (d/transact! conn [(resident->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/resident-id (first path) :assessment/payload (enc payload)}])

      :incident-screening/set
      (d/transact! conn [{:incident-screening/resident-id (first path) :incident-screening/payload (enc payload)}])

      :resident/mark-care-plan-finalized
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-care-plan! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))
            next-n (inc (next-care-plan-sequence s jurisdiction))]
        (d/transact! conn
                     [(resident->tx (assoc resident-patch :id resident-id))
                      {:care-plan-sequence/jurisdiction jurisdiction :care-plan-sequence/next next-n}
                      {:care-plan/seq (count (care-plan-history s)) :care-plan/record (enc (get result "record"))}])
        result)

      :resident/mark-incident-response-finalized
      (let [resident-id (first path)
            {:keys [result resident-patch]} (finalize-incident-response! s resident-id)
            jurisdiction (:jurisdiction (resident s resident-id))
            next-n (inc (next-incident-response-sequence s jurisdiction))]
        (d/transact! conn
                     [(resident->tx (assoc resident-patch :id resident-id))
                      {:incident-response-sequence/jurisdiction jurisdiction :incident-response-sequence/next next-n}
                      {:incident-response/seq (count (incident-response-history s)) :incident-response/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-residents [s residents]
    (when (seq residents) (d/transact! conn (mapv resident->tx (vals residents)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:residents ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [residents]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-residents s residents))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo resident set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))

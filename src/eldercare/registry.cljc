(ns eldercare.registry
  "Pure-function care-plan-finalization + incident-response-
  finalization record construction -- an append-only assisted-living
  book-of-record draft.

  Like every sibling actor's registry, there is no single international
  check-digit standard for a care-plan-finalization or incident-
  response-finalization reference number -- every facility/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `eldercare.facts` uses.

  `care-plan-review-overdue?`/`max-review-interval-days` are the FIRST
  check in this fleet's temporal-sufficiency family (established by
  `veterinary.registry/withdrawal-period-insufficient?`, reused by
  `funeral.registry/waiting-period-elapsed?`) to enforce a MAXIMUM
  elapsed-time ceiling ('not too much time may pass before the next
  review') rather than a MINIMUM required wait ('enough time must
  pass before the act'). This inverts the family's direction: `90` is
  a single representative regulatory interval commonly required for
  periodic assisted-living care-plan reviews, not a jurisdiction-by-
  jurisdiction survey of every review-cadence variant (see
  `eldercare.facts`'s own docstring for the honest scope this
  makes).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real assisted-living-management system. It builds the
  RECORD a facility would keep, not the act of finalizing the care
  plan or incident response itself (that is `eldercare.operation`'s
  `:care-plan/finalize`/`:incident-response/finalize`, always human-
  gated -- see README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  facility's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(def max-review-interval-days
  "A single representative maximum interval between periodic care-
  plan reviews -- see ns docstring for the honest simplification this
  makes (not a jurisdiction-by-jurisdiction survey of every review-
  cadence variant)."
  90)

(defn care-plan-review-overdue?
  "Does `resident`'s own `:days-since-last-care-plan-review` EXCEED
  `max-review-interval-days`? A pure ground-truth check against the
  resident's own permanent field -- the FIRST check in this fleet's
  temporal-sufficiency family to enforce a MAXIMUM ceiling (too much
  time has passed) rather than a MINIMUM wait (see ns docstring)."
  [{:keys [days-since-last-care-plan-review]}]
  (and (number? days-since-last-care-plan-review)
       (> days-since-last-care-plan-review max-review-interval-days)))

(defn register-care-plan-finalization
  "Validate + construct the CARE-PLAN-FINALIZATION registration DRAFT
  -- the facility's own legal act of finalizing a real resident care
  plan. Pure function -- does not touch any real assisted-living-
  management system; it builds the RECORD a facility would keep.
  `eldercare.governor` independently re-verifies the resident's own
  review-interval sufficiency and incident-flag status, and blocks a
  double-finalization of the same resident's care plan, before this is
  ever allowed to commit."
  [resident-id jurisdiction sequence]
  (when-not (and resident-id (not= resident-id ""))
    (throw (ex-info "care-plan-finalization: resident_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "care-plan-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "care-plan-finalization: sequence must be >= 0" {})))
  (let [plan-number (str (str/upper-case jurisdiction) "-CPL-" (zero-pad sequence 6))
        record {"record_id" plan-number
                "kind" "care-plan-finalization-draft"
                "resident_id" resident-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "plan_number" plan-number
     "certificate" (unsigned-certificate "CarePlanFinalization" plan-number plan-number)}))

(defn register-incident-response-finalization
  "Validate + construct the INCIDENT-RESPONSE-FINALIZATION
  registration DRAFT -- the facility's own legal act of finalizing a
  real incident response. Pure function -- does not touch any real
  assisted-living-management system; it builds the RECORD a facility
  would keep. `eldercare.governor` independently re-verifies the
  resident's own incident-flag status, and blocks a double-
  finalization of the same resident's incident response, before this
  is ever allowed to commit."
  [resident-id jurisdiction sequence]
  (when-not (and resident-id (not= resident-id ""))
    (throw (ex-info "incident-response-finalization: resident_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "incident-response-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "incident-response-finalization: sequence must be >= 0" {})))
  (let [response-number (str (str/upper-case jurisdiction) "-INC-" (zero-pad sequence 6))
        record {"record_id" response-number
                "kind" "incident-response-finalization-draft"
                "resident_id" resident-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "response_number" response-number
     "certificate" (unsigned-certificate "IncidentResponseFinalization" response-number response-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))

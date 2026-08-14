(ns eldercare.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for this repo: it previously had NO
  demo page and no generator at all. This namespace drives the REAL
  actor stack (`eldercare.operation` -> `eldercare.governor` ->
  `eldercare.store`, through langgraph's `g/run*`, exactly as
  `eldercare.sim` does) and renders the resulting store + audit
  channel. Nothing on the page is hand-written domain data: every
  resident id, name, jurisdiction, plan/response number, hold rule and
  hold detail string is read back out of the run.

  The scenario is this repo's own `eldercare.sim` demo driver (verified
  BEFORE this file was written to produce a sensible ledger against the
  real seeded resident ids `resident-1`..`resident-4`), extended by ONE
  additional step -- a `:care-plan/finalize` against `resident-2`,
  whose jurisdiction assessment was itself HARD-held, so no evidence
  checklist is on file. That step exercises the one HARD rule
  (`:evidence-incomplete`) the stock sim leaves untouched, bringing
  this run to SIX HARD holds covering all six of
  `eldercare.governor/check`'s HARD checks.

  Determinism: no timestamps, no random ids, no wall-clock reads. The
  page content is a pure function of `eldercare.store/demo-data` and
  the actor code, so two consecutive runs are byte-identical (verified
  by diffing two runs into separate scratch dirs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [eldercare.store :as store]
            [eldercare.facts :as facts]
            [eldercare.registry :as registry]
            [eldercare.phase :as phase]
            [eldercare.eldercareopsllm :as advisor]
            [eldercare.operation :as op]
            [langgraph.graph :as g]))

(def ^:private operator
  "The same operator context `eldercare.sim` uses."
  {:actor-id "op-1" :actor-role :care-manager :phase 3})

;; ----------------------------- the real run -----------------------------

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario that reaches every
  disposition this actor can produce.

  `resident-1` (JPN, review 30d, incident flag resolved) clears a full
  lifecycle: intake (auto-commits at phase 3 -- the only op in any
  phase's `:auto` set), a jurisdiction assessment (phase-gated, so it
  escalates even though the governor is clean -- approved), an
  incident screening (approved), a care-plan finalization (ALWAYS
  escalates: `:actuation/finalize-care-plan` is never auto at any
  phase -- approved) and an incident-response finalization (ALWAYS
  escalates, same posture -- approved).

  Six HARD holds, none of which ever reaches a human:
    resident-2 `:jurisdiction/assess`         -> :no-spec-basis
      (jurisdiction ATL is deliberately absent from `eldercare.facts/catalog`)
    resident-2 `:care-plan/finalize`          -> :evidence-incomplete
      (its assessment was itself held, so no checklist is on file)
    resident-3 `:care-plan/finalize`          -> :care-plan-review-overdue
      (120d elapsed > `registry/max-review-interval-days` 90d, recomputed
       independently by the governor from the resident's own field)
    resident-4 `:incident/screen`             -> :incident-flag-unresolved
      (the screening HARD-holds on its OWN finding)
    resident-1 `:care-plan/finalize` again    -> :already-care-plan-finalized
    resident-1 `:incident-response/finalize` again
                                              -> :already-incident-response-finalized

  Returns `{:db store :audit [...]}`. The audit vector is the
  concatenation of every run's `:audit` channel -- needed because
  `:approval-granted` facts are written to the run state but NOT to the
  store ledger, so approver attribution is only recoverable from here."
  []
  (let [db (store/seed-db)
        actor (op/build db)
        audit (atom [])
        collect! (fn [r] (swap! audit into (:audit (:state r))) r)
        run! (fn [tid request] (collect! (exec! actor tid request)))
        ok! (fn [tid] (collect! (approve! actor tid)))]

    (run! "t1" {:op :resident/intake :subject "resident-1"
                :patch {:id "resident-1" :resident-name "Sakura Tanaka"}})

    (run! "t2" {:op :jurisdiction/assess :subject "resident-1"})
    (ok! "t2")

    (run! "t3" {:op :incident/screen :subject "resident-1"})
    (ok! "t3")

    (run! "t4" {:op :care-plan/finalize :subject "resident-1"})
    (ok! "t4")

    (run! "t5" {:op :incident-response/finalize :subject "resident-1"})
    (ok! "t5")

    ;; --- HARD holds ---
    (run! "t6" {:op :jurisdiction/assess :subject "resident-2" :no-spec? true})
    (run! "t7" {:op :care-plan/finalize :subject "resident-2"})

    (run! "t8" {:op :jurisdiction/assess :subject "resident-3"})
    (ok! "t8")
    (run! "t9" {:op :care-plan/finalize :subject "resident-3"})

    (run! "t10" {:op :incident/screen :subject "resident-4"})

    (run! "t11" {:op :care-plan/finalize :subject "resident-1"})
    (run! "t12" {:op :incident-response/finalize :subject "resident-1"})

    {:db db :audit @audit}))

;; ----------------------------- html helpers -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw [v] (if (keyword? v) (name v) (str v)))

(defn- row [& cells]
  (str "        <tr>" (str/join (map #(str "<td>" % "</td>") cells)) "</tr>"))

(defn- table [headers rows]
  (str "    <table>\n"
       "      <thead><tr>" (str/join (map #(str "<th>" (esc %) "</th>") headers)) "</tr></thead>\n"
       "      <tbody>\n"
       (str/join "\n" rows) "\n"
       "      </tbody>\n"
       "    </table>\n"))

(defn- section [title lead body]
  (str "  <section class=\"card\">\n"
       "    <h2>" (esc title) "</h2>\n"
       "    <p class=\"muted\">" lead "</p>\n"
       body
       "  </section>\n"))

(defn- ok [s] (str "<span class=\"ok\">" (esc s) "</span>"))
(defn- warn [s] (str "<span class=\"warn\">" (esc s) "</span>"))
(defn- crit [s] (str "<span class=\"critical\">" (esc s) "</span>"))
(defn- muted [s] (str "<span class=\"muted\">" (esc s) "</span>"))
(defn- code [s] (str "<code>" (esc s) "</code>"))

;; ----------------------------- approver attribution -----------------------------
;;
;; Measured on this repo, not assumed. `eldercare.operation` builds a
;; commit record as {:effect :path :value :payload}; only :payload
;; carries :approved-by. This store reads :payload for
;; :assessment/set and :incident-screening/set (so the approver IS
;; retained there), reads :value for :resident/upsert, and for the two
;; actuation effects ignores both and drafts the record from
;; `eldercare.registry` (so no approver field exists on the record at
;; all).
;;
;; Rather than hardcode "this is broken", `approver` DERIVES the answer
;; at render time by looking for an approver key actually present in
;; the retained value. If the store later starts retaining it, these
;; cells flip to "retained" with no change here.

(defn- granted-by
  "The approver from the run's `:approval-granted` audit fact for this
  op+subject, or nil."
  [audit op* subject]
  (some->> audit
           (filter #(and (= :approval-granted (:t %))
                         (= op* (:op %))
                         (= subject (:subject %))))
           last
           :by))

(defn- approver
  "Returns {:who str :retained? bool} or nil. `retained` is whatever the
  store actually kept; `audit` is the fallback source of truth."
  [retained-value audit op* subject]
  (let [in-record (or (:approved-by retained-value)
                      (get retained-value "approved_by"))]
    (cond
      in-record {:who in-record :retained? true}
      (granted-by audit op* subject) {:who (granted-by audit op* subject) :retained? false}
      :else nil)))

(defn- approver-cell [a]
  (cond
    (nil? a) (muted "—")
    (:retained? a) (str (ok (:who a)) " " (muted "(retained in record)"))
    :else (str (warn (:who a)) " "
               (muted "(audit only — not retained in record)"))))

;; ----------------------------- sections -----------------------------

(defn- last-fact-for [ledger id]
  (last (filter #(= (:subject %) id) ledger)))

(defn- status-cell [ledger id]
  (let [f (last-fact-for ledger id)]
    (cond
      (nil? f) (muted "no activity")
      (= :governor-hold (:t f))
      (crit (str "HARD hold · " (kw (-> f :violations first :rule))))
      (= :committed (:t f)) (ok (str "committed · " (kw (:op f))))
      :else (muted (kw (:t f))))))

(defn- resident-rows [db ledger]
  (for [{:keys [id resident-name jurisdiction days-since-last-care-plan-review
                incident-flag-resolved? care-plan-finalized?
                incident-response-finalized? plan-number response-number]}
        (store/all-residents db)]
    (row (code id)
         (esc resident-name)
         (if (facts/spec-basis jurisdiction)
           (esc jurisdiction)
           (str (esc jurisdiction) " " (crit "no spec-basis")))
         (let [d days-since-last-care-plan-review]
           (if (> d registry/max-review-interval-days)
             (crit (str d "d > " registry/max-review-interval-days "d"))
             (ok (str d "d ≤ " registry/max-review-interval-days "d"))))
         (if incident-flag-resolved? (ok "resolved") (crit "UNRESOLVED"))
         (if care-plan-finalized?
           (str (ok "finalized") " " (code plan-number))
           (muted "not finalized"))
         (if incident-response-finalized?
           (str (ok "finalized") " " (code response-number))
           (muted "not finalized"))
         (status-cell ledger id))))

(defn- hold-rows [ledger]
  (for [f (filter #(= :governor-hold (:t %)) ledger)
        v (:violations f)]
    (row (code (kw (:rule v)))
         (code (kw (:op f)))
         (code (:subject f))
         (esc (:detail v))
         (esc (:confidence f)))))

(defn- gate-rows
  "Derived from `eldercare.phase/phases` and the REAL advisor's own
  `:stake` for each op -- not a hand-written description, so it cannot
  drift out of sync with the code."
  []
  (let [probe (store/seed-db)
        auto3 (get-in phase/phases [3 :auto])
        stake-of (fn [o]
                   (:stake (advisor/infer probe {:op o :subject "resident-1"
                                                 :patch {:id "resident-1"}})))]
    (for [o (sort (map str phase/write-ops))
          :let [o (keyword (subs o 1))
                st (stake-of o)]]
      (row (code o)
           (if (contains? auto3 o)
             (ok "auto-commit when governor-clean")
             (warn "human approval required"))
           (if st (crit (kw st)) (muted "—"))
           (if st
             (crit "never auto at ANY phase (2 independent layers)")
             (muted "phase-gated only"))))))

(defn- register-rows [records audit op* number-key kind]
  (for [r records
        :let [rid (get r "record_id")
              subject (get r "resident_id")
              a (approver r audit op* subject)]]
    (row (code rid)
         (esc kind)
         (code subject)
         (esc (get r "jurisdiction"))
         (if (get r "immutable") (ok "immutable") (muted "—"))
         (approver-cell a)
         (muted number-key))))

(defn- catalog-rows [db]
  (let [used (set (map :jurisdiction (store/all-residents db)))]
    (for [iso3 (sort (into used (keys facts/catalog)))
          :let [sb (facts/spec-basis iso3)]]
      (row (code iso3)
           (if sb (esc (:name sb)) (crit "NOT IN CATALOG"))
           (if sb (esc (:owner-authority sb)) (muted "—"))
           (if sb (esc (:legal-basis sb)) (muted "—"))
           (if sb (str (count (:required-evidence sb)) " items") (muted "0"))
           (if (used iso3) (ok "in use by a seeded resident") (muted "catalog only"))))))

(defn- ledger-rows [ledger]
  (for [f ledger]
    (row (if (= :governor-hold (:t f)) (crit (kw (:t f))) (ok (kw (:t f))))
         (code (kw (:op f)))
         (code (:subject f))
         (esc (:actor f))
         (esc (or (some->> (:basis f) (map kw) (str/join ", ")) ""))
         (esc (or (:summary f) "")))))

;; ----------------------------- document -----------------------------

(defn render
  "Renders the operator console from a completed `run-demo!` result."
  [{:keys [db audit]}]
  (let [ledger (vec (store/ledger db))
        holds (filter #(= :governor-hold (:t %)) ledger)
        cov (facts/coverage)]
    (str
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-8730 · residential eldercare</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Residential care activities for the elderly (ISIC 8730) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · care-plan &amp; incident-response finalization always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     (section
      "Residents"
      (str "Demo snapshot — build-time-generated from <code>eldercare.store</code> "
           "through the real actor graph by <code>eldercare.render-html</code> "
           "(<code>clojure -M:dev:render-html</code>). Every row is read back out of "
           "the store after the run; none of it is typed into this page.")
      (table ["Resident" "Name" "Jurisdiction" "Care-plan review age"
              "Incident flag" "Care plan" "Incident response" "Last op status"]
             (resident-rows db ledger)))

     (section
      (str "Governor HARD holds this run (" (count holds) ")")
      (str "HARD violations cannot be overridden by an approver — they never reach a "
           "human at all. Rule names and detail strings below are the governor's own "
           "output, copied verbatim out of the audit ledger.")
      (table ["Rule" "Op" "Resident" "Governor detail" "Advisor confidence"]
             (hold-rows ledger)))

     (section
      "Action gate (Eldercare Governor × rollout phase 3)"
      (str "Derived at build time from <code>eldercare.phase/phases</code> and the real "
           "advisor's own <code>:stake</code> for each op — not a hand-written table, so "
           "it cannot drift from the code. <code>:care-plan/finalize</code> and "
           "<code>:incident-response/finalize</code> are absent from every phase's "
           "<code>:auto</code> set as a permanent structural fact.")
      (table ["Op" "Phase 3 disposition" "Stake" "Actuation posture"]
             (gate-rows)))

     (section
      "Care-plan finalization records (draft)"
      (str "Append-only drafts built by <code>eldercare.registry</code>. These are the "
           "records a facility would keep — this actor never touches a real care-"
           "management system, and every certificate it produces is unsigned.")
      (table ["Record" "Kind" "Resident" "Jurisdiction" "Immutability" "Approved by" "Sequence source"]
             (register-rows (store/care-plan-history db) audit
                            :care-plan/finalize "jurisdiction-scoped CPL sequence"
                            "care-plan-finalization-draft")))

     (section
      "Incident-response finalization records (draft)"
      (str "The second actuation lifecycle, with its own history, sequence counter and "
           "double-actuation guard.")
      (table ["Record" "Kind" "Resident" "Jurisdiction" "Immutability" "Approved by" "Sequence source"]
             (register-rows (store/incident-response-history db) audit
                            :incident-response/finalize "jurisdiction-scoped INC sequence"
                            "incident-response-finalization-draft")))

     (section
      "Jurisdiction spec-basis catalog"
      (str "From <code>eldercare.facts/catalog</code>. Coverage is reported honestly: "
           (:covered cov) " of " (:requested cov) " catalogued jurisdictions carry an "
           "official spec-basis. A jurisdiction absent from this table has NO spec-basis, "
           "full stop — the advisor must not fabricate one, and the governor holds if it tries.")
      (table ["ISO3" "Jurisdiction" "Regulator" "Legal basis" "Required evidence" "Use in this run"]
             (catalog-rows db)))

     (section
      (str "Audit ledger this run (" (count ledger) " facts)")
      (str "Append-only decision-fact log — every commit and every hold this scenario "
           "produced, in order. Note that <code>:approval-granted</code> facts live in the "
           "run's audit channel rather than the store ledger, which is why the approver "
           "columns above are joined from there.")
      (table ["Fact" "Op" "Resident" "Actor" "Basis" "Summary"]
             (ledger-rows ledger)))

     "</main>\n"
     "<footer>\n"
     "  <p>Generated by <code>eldercare.render-html</code> from a real "
     "<code>eldercare.operation</code> actor run over "
     "<code>eldercare.store/demo-data</code>. Deterministic: no timestamps, no random "
     "ids — two consecutive runs are byte-identical. Certificates are drafts and unsigned; "
     "signature is the facility's own act, not this actor's.</p>\n"
     "</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        {:keys [db] :as result} (run-demo!)
        ledger (vec (store/ledger db))
        holds (filter #(= :governor-hold (:t %)) ledger)]
    ;; Build-time invariant, not a convention: a console that shows no HARD
    ;; hold is not demonstrating this actor's central claim (that the
    ;; governor can refuse the advisor un-overridably). Fail the build.
    (when (zero? (count holds))
      (throw (ex-info "render-html: scenario produced NO :governor-hold records; refusing to emit a console that cannot demonstrate an un-overridable HARD hold"
                      {:ledger-facts (count ledger)
                       :holds 0})))
    (let [html (render result)]
      (.mkdirs (java.io.File. (or (.getParent (java.io.File. ^String out)) ".")))
      (spit out html)
      (println "wrote" out
               (str "(" (count ledger) " ledger facts, "
                    (count holds) " HARD holds, "
                    (count (store/care-plan-history db)) " care-plan drafts, "
                    (count (store/incident-response-history db)) " incident-response drafts, "
                    (count html) " chars)")))))

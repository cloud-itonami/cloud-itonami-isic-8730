(ns eldercare.governor
  "Eldercare Governor -- the independent compliance layer that earns
  the EldercareOps-LLM the right to commit. The LLM has no notion of
  jurisdictional assisted-living licensing law, whether a resident's
  own care-plan review is overdue, whether an incident flag is still
  unresolved, or when an act stops being a draft and becomes a real-
  world care-plan or incident-response finalization, so this MUST be a
  separate system able to *reject* a proposal and fall back to HOLD --
  the residential-eldercare analog of `cloud-itonami-isic-6512`'s
  CasualtyGovernor.

  Six checks, in priority order, ALL HARD violations: a human approver
  CANNOT override them (you don't get to approve your way past a
  fabricated jurisdiction spec-basis, incomplete evidence, an overdue
  care-plan review, an unresolved incident flag, or a double
  finalization). The confidence/actuation gate is SOFT: it asks a
  human to look (low confidence / actuation), and the human may
  approve -- but see `eldercare.phase`: for `:stake :actuation/
  finalize-care-plan`/`:actuation/finalize-incident-response` (a real
  care-plan or incident-response finalization) NO phase ever allows
  auto-commit either. Two independent layers agree that actuation is
  always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`eldercare.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:care-plan/finalize`/
                                       `:incident-response/finalize`,
                                       has the jurisdiction actually
                                       been assessed with a full
                                       resident-evidence checklist on
                                       file?
    3. Care-plan review overdue    -- for `:care-plan/finalize`,
                                       INDEPENDENTLY recompute whether
                                       the resident's own `:days-since-
                                       last-care-plan-review` EXCEEDS
                                       `eldercare.registry/max-review-
                                       interval-days`
                                       (`eldercare.registry/care-plan-
                                       review-overdue?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. This is
                                       the FIRST check in this fleet's
                                       temporal-sufficiency family
                                       (`veterinary.governor/
                                       withdrawal-period-insufficient-
                                       violations`, `funeral.governor/
                                       waiting-period-not-elapsed-
                                       violations`) to enforce a
                                       MAXIMUM elapsed-time ceiling
                                       ('not too much time may pass')
                                       rather than a MINIMUM required
                                       wait -- see `eldercare.registry`
                                       ns docstring.
    4. Incident flag unresolved    -- for `:care-plan/finalize`/
                                       `:incident-response/finalize`,
                                       reported by THIS proposal itself
                                       (an `:incident/screen` that just
                                       found an unresolved flag), or
                                       already on file for the resident
                                       (`:incident/screen`/either
                                       actuation op). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME discipline
                                       `casualty.governor/sanctions-
                                       violations`/`marketadmin.
                                       governor/surveillance-flag-
                                       unresolved-violations`/`testlab.
                                       governor/calibration-not-current-
                                       violations`/`clinic.governor/
                                       credential-not-current-
                                       violations`/`registrar.governor/
                                       integrity-flag-unresolved-
                                       violations`/`wagering.governor/
                                       patron-flag-unresolved-
                                       violations`/`veterinary.
                                       governor/credential-not-current-
                                       violations`/`funeral.governor/
                                       authorization-unverified-
                                       violations`/`repairshop.
                                       governor/safety-test-not-passed-
                                       violations` established -- the
                                       TENTH distinct application of
                                       this exact discipline. Like
                                       `parksafety.governor`'s
                                       inspection-not-passed check
                                       (ADR-2607071922 Decision 5), this
                                       is exercised in tests/demo via
                                       `:incident/screen` DIRECTLY, not
                                       via an actuation op against an
                                       unscreened resident -- see this
                                       ns's own test suite.
    5. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:care-plan/
                                       finalize`/`:incident-response/
                                       finalize` (REAL acts) ->
                                       escalate.

  Two more guards, double-finalization prevention, are enforced but
  NOT listed as numbered HARD checks above because they need no
  upstream comparison at all -- `already-care-plan-finalized-
  violations`/`already-incident-response-finalized-violations` refuse
  to finalize the SAME resident's care plan/incident response twice,
  off dedicated `:care-plan-finalized?`/`:incident-response-
  finalized?` facts (never a `:status` value) -- the SAME 'check a
  dedicated boolean, not status' discipline `accounting.governor`'s/
  `marketadmin.governor`'s/`testlab.governor`'s/`clinic.governor`'s/
  `registrar.governor`'s/`wagering.governor`'s/`veterinary.
  governor`'s/`funeral.governor`'s/`repairshop.governor`'s/
  `parksafety.governor`'s guards establish, informed by
  `cloud-itonami-isic-6492`'s status-lifecycle bug (ADR-2607071320)."
  (:require [eldercare.facts :as facts]
            [eldercare.registry :as registry]
            [eldercare.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Finalizing a real care plan and finalizing a real incident response
  are the two real-world actuation events this actor performs -- a
  two-member set, matching `cloud-itonami-isic-6512`'s/`6622`'s/
  `6520`'s/`6530`'s/`6820`'s/`6920`'s/`6611`'s/`8530`'s/`9200`'s/
  `9521`'s dual-actuation shape."
  #{:actuation/finalize-care-plan :actuation/finalize-incident-response})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:care-plan/finalize`/`:incident-
  response/finalize`) proposal with no spec-basis citation is a HARD
  violation -- never invent a jurisdiction's assisted-living licensing
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :care-plan/finalize :incident-response/finalize} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:care-plan/finalize`/`:incident-response/finalize`, the
  jurisdiction's required resident-consent/care-assessment/caregiver-
  certification/incident-report evidence must actually be satisfied --
  do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:care-plan/finalize :incident-response/finalize} op)
    (let [r (store/resident st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction r) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(同意書/アセスメント記録/資格確認記録/事故報告書等)が充足していない状態での提案"}]))))

(defn- care-plan-review-overdue-violations
  "For `:care-plan/finalize`, INDEPENDENTLY recompute whether the
  resident's own `:days-since-last-care-plan-review` exceeds
  `eldercare.registry/max-review-interval-days` via `eldercare.
  registry/care-plan-review-overdue?` -- needs no proposal inspection
  or stored-verdict lookup at all, since its input is a permanent
  ground-truth field already on the resident."
  [{:keys [op subject]} st]
  (when (= op :care-plan/finalize)
    (let [r (store/resident st subject)]
      (when (registry/care-plan-review-overdue? r)
        [{:rule :care-plan-review-overdue
          :detail (str subject " の前回ケアプラン見直しから"
                      (:days-since-last-care-plan-review r) "日経過し、上限("
                      registry/max-review-interval-days "日)を超過している")}]))))

(defn- incident-flag-unresolved-violations
  "An unresolved incident flag -- reported by THIS proposal (e.g. an
  `:incident/screen` that itself just found an unresolved flag), or
  already on file in the store for the resident (`:incident/screen`/
  either actuation op) -- is a HARD, un-overridable hold. Evaluated
  UNCONDITIONALLY (not scoped to a specific op) so the screening op
  itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        resident-id (when (contains? #{:incident/screen :care-plan/finalize :incident-response/finalize} op) subject)
        hit-on-file? (and resident-id (= :unresolved (:verdict (store/incident-screening-of st resident-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :incident-flag-unresolved
        :detail "未解決の事故フラグが残っている入居者に対する提案は進められない"}])))

(defn- already-care-plan-finalized-violations
  "For `:care-plan/finalize`, refuses to finalize the SAME resident's
  care plan twice, off a dedicated `:care-plan-finalized?` fact (never
  a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :care-plan/finalize)
    (when (store/resident-care-plan-already-finalized? st subject)
      [{:rule :already-care-plan-finalized
        :detail (str subject " は既にケアプラン確定済み")}])))

(defn- already-incident-response-finalized-violations
  "For `:incident-response/finalize`, refuses to finalize the SAME
  resident's incident response twice, off a dedicated `:incident-
  response-finalized?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :incident-response/finalize)
    (when (store/resident-incident-response-already-finalized? st subject)
      [{:rule :already-incident-response-finalized
        :detail (str subject " は既に事故対応確定済み")}])))

(defn check
  "Censors an EldercareOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (care-plan-review-overdue-violations request st)
                           (incident-flag-unresolved-violations request proposal st)
                           (already-care-plan-finalized-violations request st)
                           (already-incident-response-finalized-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})

(ns eldercare.eldercareopsllm
  "EldercareOps-LLM client -- the *contained intelligence node* for the
  residential-eldercare actor.

  It normalizes resident intake, drafts a per-jurisdiction assisted-
  living evidence checklist, screens residents for an unresolved
  incident flag, drafts the care-plan-finalization action, and drafts
  the incident-response-finalization action. CRITICAL: it is a smart-
  but-untrusted advisor. It returns a *proposal* (with a rationale +
  the fields it cited), never a committed record or a real care-plan/
  incident-response finalization. Every output is censored downstream
  by `eldercare.governor` before anything touches the SSoT, and
  `:care-plan/finalize`/`:incident-response/finalize` proposals NEVER
  auto-commit at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :actuation/finalize-care-plan | :actuation/finalize-incident-response | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [eldercare.facts :as facts]
            [eldercare.registry :as registry]
            [eldercare.store :as store]
            [langchain.model :as model]))

(defn- normalize-intake
  "Directory upsert -- the LLM only normalizes/validates the patch; it
  does not invent the resident, review interval or jurisdiction. High
  confidence, low stakes."
  [_db {:keys [patch]}]
  {:summary    (str "入居者記録更新: " (pr-str (keys patch)))
   :rationale  "入力 patch の正規化のみ。新規事実の生成なし。"
   :cites      (vec (keys patch))
   :effect     :resident/upsert
   :value      patch
   :stake      nil
   :confidence 0.97})

(defn- assess-jurisdiction
  "Per-jurisdiction assisted-living evidence checklist draft.
  `:no-spec?` injects the failure mode we must defend against:
  proposing a checklist for a jurisdiction with NO official spec-basis
  in `eldercare.facts` -- the Eldercare Governor must reject this
  (never invent a jurisdiction's requirements)."
  [db {:keys [subject no-spec?]}]
  (let [r (store/resident db subject)
        iso3 (if no-spec? "ATL" (:jurisdiction r))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "eldercare.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :assessment/set
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :stake      nil
       :confidence 0.9}
      {:summary    (str iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :assessment/set
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      nil
       :confidence 0.9})))

(defn- screen-incident
  "Incident-flag screening draft. `:incident-flag-resolved?` on the
  resident record injects the failure mode: the Eldercare Governor
  must HOLD, un-overridably, on any unresolved incident flag."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)]
    (cond
      (nil? r)
      {:summary "対象入居者が見つかりません" :rationale "no resident record"
       :cites [] :effect :incident-screening/set :value {:resident-id subject :verdict :unknown}
       :stake nil :confidence 0.0}

      (false? (:incident-flag-resolved? r))
      {:summary    (str (:resident-name r) ": 未解決の事故フラグを検出")
       :rationale  "スクリーニングが未解決の事故フラグを検出。人手確認とホールドが必須。"
       :cites      [:incident-check]
       :effect     :incident-screening/set
       :value      {:resident-id subject :verdict :unresolved}
       :stake      nil
       :confidence 0.95}

      :else
      {:summary    (str (:resident-name r) ": 事故フラグ解決済み")
       :rationale  "事故フラグスクリーニング完了。"
       :cites      [:incident-check]
       :effect     :incident-screening/set
       :value      {:resident-id subject :verdict :resolved}
       :stake      nil
       :confidence 0.9})))

(defn- propose-care-plan-finalization
  "Draft the actual CARE-PLAN-FINALIZATION action -- finalizing a real
  resident care plan. ALWAYS `:stake :actuation/finalize-care-plan` --
  this is a REAL-WORLD act (a real care regimen takes effect), never a
  draft the actor may auto-run. See README `Actuation`: no phase ever
  adds this op to a phase's `:auto` set (`eldercare.phase`); the
  governor also always escalates on `:actuation/finalize-care-plan`.
  Two independent layers agree, deliberately."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)
        overdue? (and r (registry/care-plan-review-overdue? r))]
    {:summary    (str subject " 向けケアプラン確定提案"
                      (when r (str " (resident=" (:resident-name r) ")")))
     :rationale  (if r
                   (str "days-since-last-care-plan-review=" (:days-since-last-care-plan-review r)
                        " max-review-interval-days=" registry/max-review-interval-days)
                   "入居者が見つかりません")
     :cites      (if r [subject] [])
     :effect     :resident/mark-care-plan-finalized
     :value      {:resident-id subject}
     :stake      :actuation/finalize-care-plan
     :confidence (if overdue? 0.3 0.9)}))

(defn- propose-incident-response-finalization
  "Draft the actual INCIDENT-RESPONSE-FINALIZATION action -- finalizing
  a real incident response. ALWAYS `:stake :actuation/finalize-
  incident-response` -- this is a REAL-WORLD act (a regulator-facing
  incident record becomes final), never a draft the actor may auto-
  run. See README `Actuation`: no phase ever adds this op to a phase's
  `:auto` set (`eldercare.phase`); the governor also always escalates
  on `:actuation/finalize-incident-response`. Two independent layers
  agree, deliberately."
  [db {:keys [subject]}]
  (let [r (store/resident db subject)]
    {:summary    (str subject " 向け事故対応確定提案"
                      (when r (str " (resident=" (:resident-name r) ")")))
     :rationale  (if r
                   (str "incident-flag-resolved?=" (:incident-flag-resolved? r))
                   "入居者が見つかりません")
     :cites      (if r [subject] [])
     :effect     :resident/mark-incident-response-finalized
     :value      {:resident-id subject}
     :stake      :actuation/finalize-incident-response
     :confidence (if (and r (:incident-flag-resolved? r)) 0.9 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :resident/intake              (normalize-intake db request)
    :jurisdiction/assess              (assess-jurisdiction db request)
    :incident/screen                     (screen-incident db request)
    :care-plan/finalize                     (propose-care-plan-finalization db request)
    :incident-response/finalize                (propose-incident-response-finalization db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは高齢者施設のケアプラン確定・事故対応確定エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:resident/upsert|:assessment/set|:incident-screening/set|"
       ":resident/mark-care-plan-finalized|:resident/mark-incident-response-finalized) "
       ":stake(:actuation/finalize-care-plan か :actuation/finalize-incident-response か nil) :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :jurisdiction/assess          {:resident (store/resident st subject)}
    :incident/screen              {:resident (store/resident st subject)}
    :care-plan/finalize           {:resident (store/resident st subject)}
    :incident-response/finalize   {:resident (store/resident st subject)}
    {:resident (store/resident st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Eldercare Governor
  escalates/holds -- an LLM hiccup can never auto-finalize a care plan
  or auto-finalize an incident response."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :eldercareopsllm-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})

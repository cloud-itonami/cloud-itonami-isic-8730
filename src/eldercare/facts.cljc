(ns eldercare.facts
  "Per-jurisdiction assisted-living/eldercare regulatory catalog -- the
  G2-style spec-basis table the Eldercare Governor checks every
  jurisdiction/assess proposal against ('did the advisor cite an
  OFFICIAL public source for this jurisdiction's assisted-living
  licensing requirements, or did it invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official assisted-
  living/eldercare regulator (see `:provenance`); they are a STARTING
  catalog, not a from-scratch survey of all ~194 jurisdictions.
  Extending coverage is additive: add one map to `catalog`, cite a real
  source, done -- never invent a jurisdiction's requirements to make
  coverage look bigger.

  Like `wagering.facts`'s/`parksafety.facts`'s federated jurisdictions,
  the USA and DEU entries here cite a single representative regulator
  (assisted-living/residential-care licensing is regulated at state
  level in both countries) rather than a state-by-state survey -- an
  honest representative citation, the same simplification every prior
  catalog makes when a jurisdiction's real regulatory structure is
  itself federated. `eldercare.registry/max-review-interval-days`'s
  own docstring explains why this R0 does not attempt to cite a
  jurisdiction-specific care-plan-review cadence for each entry
  below -- the review-interval figure is a single representative
  regulatory interval, not a per-jurisdiction survey.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  resident-consent/care-assessment/caregiver-certification/incident-
  report evidence set submitted in some form; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "厚生労働省 (Ministry of Health, Labour and Welfare, MHLW)"
          :legal-basis "介護保険法 (Long-Term Care Insurance Act) + 老人福祉法 (Act on Social Welfare for the Elderly)"
          :national-spec "介護サービス事業者指定基準・ケアプラン運用基準"
          :provenance "https://www.mhlw.go.jp/"
          :required-evidence ["入居者同意書/後見人書面 (resident consent/POA documentation)"
                              "アセスメント記録 (care-assessment documentation)"
                              "介護職員資格確認記録 (caregiver certification)"
                              "事故報告書 (incident-report documentation)"]}
   "USA" {:name "United States"
          :owner-authority "California Department of Social Services, Community Care Licensing Division"
          :legal-basis "California Health and Safety Code, Division 2, Chapter 3.2 (Residential Care Facilities for the Elderly Act)"
          :national-spec "RCFE licensing and care-plan requirements"
          :provenance "https://www.cdss.ca.gov/inforesources/community-care-licensing/residential-care-for-the-elderly"
          :required-evidence ["Resident consent/POA documentation"
                              "Care-assessment documentation"
                              "Caregiver certification"
                              "Incident-report documentation"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Care Quality Commission (CQC)"
          :legal-basis "Health and Social Care Act 2008 (Regulated Activities) Regulations 2014"
          :national-spec "CQC Fundamental Standards for adult social care"
          :provenance "https://www.cqc.org.uk/"
          :required-evidence ["Resident consent/POA documentation"
                              "Care-assessment documentation"
                              "Caregiver certification"
                              "Incident-report documentation"]}
   "DEU" {:name "Germany"
          :owner-authority "Heimaufsichtsbehörden der Länder (state care-home supervisory authorities)"
          :legal-basis "Wohn- und Betreuungsvertragsgesetz (WBVG) + Landesheimgesetze (state care-home acts)"
          :national-spec "Heimaufsichtsrechtliche Betreuungs- und Dokumentationspflichten"
          :provenance "https://www.gesetze-im-internet.de/wbvg/"
          :required-evidence ["Einwilligungserklärung/Vollmachtsnachweis (resident consent/POA documentation)"
                              "Pflegeassessment (care-assessment documentation)"
                              "Pflegekraftqualifikationsnachweis (caregiver certification)"
                              "Vorfallbericht (incident-report documentation)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to finalize a
  care plan or incident response on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-8730 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `eldercare.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))

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
                              "Vorfallbericht (incident-report documentation)"]}
   ;; AUS -- unlike Australian state-based private-security/guard licensing,
   ;; residential aged care is a genuinely FEDERAL (Commonwealth) matter:
   ;; verified directly against the Aged Care Act 2024 (Cth) No. 104, 2024
   ;; text at legislation.gov.au (fetched this session), which is a single
   ;; national Act administered by a single national regulator, not a
   ;; state-by-state licensing scheme like DEU/USA above. That Act came
   ;; into force 1 November 2025, replacing the Aged Care Act 1997 (whose
   ;; own legislation.gov.au compilation history confirms its last
   ;; compilation ended 31 Oct 2025). Section numbers below (ss 14-16, 27-42,
   ;; 58-60, 104-105, 146, 164-165A, 344, 379, 440, 481) are taken directly
   ;; from that Act's own table of contents as rendered at
   ;; legislation.gov.au/C2024A00104/latest/text. The "7 standards" figure
   ;; and the "Aged Care Rules 2025" name were confirmed on the regulator's
   ;; own site (agedcarequality.gov.au) via an Internet Archive capture
   ;; (https://web.archive.org/web/20251110190847/https://www.agedcarequality.gov.au/providers/quality-standards/strengthened-aged-care-quality-standards,
   ;; captured 2025-11-10, i.e. after the Act's commencement) because the
   ;; live agedcarequality.gov.au did not respond to direct fetch this
   ;; session (repeated timeouts consistent with bot-mitigation, not
   ;; investigated further per this workspace's policy against CAPTCHA/
   ;; bot-detection evasion) -- note this is a *strengthened* 7-standard
   ;; framework that superseded an earlier 8-standard framework under the
   ;; 1997 Act, so do not assume "8 standards" without checking the date.
   "AUS" {:name "Australia"
          :owner-authority "Aged Care Quality and Safety Commission (Commonwealth regulator; Aged Care Act 2024 (Cth) Pt 3 s 344)"
          :legal-basis "Aged Care Act 2024 (Cth) No. 104, 2024 -- s 104 (registration of providers) + s 146 (compliance with Aged Care Quality Standards); in force since 1 November 2025, replacing the Aged Care Act 1997"
          :national-spec "Strengthened Aged Care Quality Standards (7 standards) under the Aged Care Rules 2025, Aged Care Act 2024 ss 15 & 146"
          :provenance "https://www.legislation.gov.au/C2024A00104/latest/text"
          :required-evidence ["Supporter registration/consent documentation (Aged Care Act 2024 Pt 4, ss 27-42)"
                              "Aged care needs-assessment documentation (ss 58-60)"
                              "Aged care worker screening database record / Aged Care Code of Conduct compliance (ss 14, 379)"
                              "Reportable incident management record (ss 164, 165A)"]}})

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

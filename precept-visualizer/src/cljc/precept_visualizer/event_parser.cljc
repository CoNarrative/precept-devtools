(ns precept-visualizer.event-parser
  (:require [clojure.spec.alpha :as s]
            [precept.spec.lang :as lang]
            [precept.spec.event :as precept-event]))
            ;[precept-visualizer.util :as util]))

;; CLJC failing on reload, copying from util here
(defn display-eav [m] ((juxt :e :a :v) m))

(def eav-this-map
  {:e '(:e this) :a '(:a this) :v '(:v this)})


(defn first-variable-binding [condition]
  (some #(when (clojure.string/starts-with? (str %) "?") %)
    condition))


(defn binding-declaration?
  "Tests Clara syntax for an initial binding to a value in a Tuple.
  e.g. (= ?foo (:e this))"
  [sexpr eav-kw]
  (boolean (and (= (count sexpr) 3)
             (= (eav-kw eav-this-map) (last sexpr))
             (= '= (first sexpr))
             (s/valid? ::lang/variable-binding (second sexpr)))))

(defn dedupe-matches-into-eavs
  "Deduplicates a condition's matches, which contain the same fact more than once.
  Returns in [e a v] format."
  [matches]
  (distinct (mapv #(if (map? (first %))
                     (display-eav (first %))
                     (first %))
              matches)))

(defn value-constraint-for-slot?
  [sexpr eav-kw]
  (boolean (and (= (count sexpr) 3)
             (= (eav-kw eav-this-map) (last sexpr))
             (= '= (first sexpr))
             (not (s/valid? ::lang/variable-binding (second sexpr))))))

(defn constraints-for-slot [eav-kw constraints]
  (reduce
    (fn [acc constraint]
      (if (contains? (set constraint) (eav-kw eav-this-map))
        (conj acc (second constraint))
        acc))
    []
    constraints))

;; Assumes max depth 1 of sexprs in conditions
(defn ast->positional
  "Parses a single condition of Clara's LHS ast into Precept syntax.
  Should be called in order of LHS declaration.
  `slot->expression-binding` - atom {} of user-defined variable binding for each
                               :e :a :v slot
   `condition` - Clara ast of condition to be parsed.
                 keys are :type (i.e. type of fact, corresponding to attribute)
                 and :constraints (a list, in Clara syntax)
  `eav-kw` - Keyword of the slot to parse
  "
  [slot->expression-binding condition eav-kw]
  (let [ast->binding-symbols (reduce
                               (fn [acc condition]
                                 (if (binding-declaration? condition eav-kw)
                                   (let [variable-binding (first-variable-binding condition)
                                         eav-binding (eav-kw eav-this-map)]
                                     (do
                                       (swap! slot->expression-binding assoc eav-kw variable-binding)
                                       (assoc acc
                                         (list '= variable-binding eav-binding) variable-binding
                                         eav-binding variable-binding)))
                                   acc))
                               {}
                               (:constraints condition))
        bound-variable-sym (first (vals ast->binding-symbols))
        with-symbols-injected (clojure.walk/postwalk-replace
                                ast->binding-symbols
                                (:constraints condition))
        forms-for-requested-slot  (filter #(some #{bound-variable-sym} %)
                                    with-symbols-injected)
        bound-variable-decl (list '= bound-variable-sym bound-variable-sym)]

    (cond
      (> (count forms-for-requested-slot) 1)
      ;; remove duplicate added by postwalk replace for variable binding declaration
      (some #(when (not= bound-variable-decl %) %)
        forms-for-requested-slot)

      (and (= (first forms-for-requested-slot) bound-variable-decl)
        (some? bound-variable-sym))
      bound-variable-sym

      ; With boolean operators, a single condition can constrain the same slot
      ; multiple times. e.g. [:and [?e :foo] [:not [:global :foo]]]
      ; In this case we have a single condition still with one variable binding
      ; (which may be bound in this condition or a previous one)
      ; and a value constraint
      (and (= eav-kw :e)
           (nil? bound-variable-sym))
      (let [e-constraints (constraints-for-slot :e (:constraints condition))]
        (cond
          (empty? e-constraints)
          '_

          (= 1 (count e-constraints))
          (first e-constraints)

          true
          (throw (ex-info "Unexpected number of constraints" (:constraints condition)))))

      ;; Use stored symbol name for variable for this slot
      ;; when this expression does not contain a declaration for it
      (nil? bound-variable-sym)
      (let [bound-symbol-for-slot (eav-kw @slot->expression-binding)]
        (some (fn [form]
                (when (= bound-symbol-for-slot
                        (first-variable-binding form))
                  form))
          with-symbols-injected))

      true
      (first forms-for-requested-slot))))

(defn parse-as-positional-tuple
  [slot->expression-binding acc ast]
  (let [e (ast->positional
            slot->expression-binding ast :e)
        a (:type ast)
        v (ast->positional
            slot->expression-binding ast :v)]
    (conj acc [(filterv #(not (nil? %)) [e a v])])))


;; ::precept-event/test-condition
(s/def ::test-condition
  #(= #{:constraints}
      (set (keys %))))


(s/def ::fact-binding-expression
  #(contains? % :fact-binding))

(def arity-1-bool-op #{:not})
(def arity-n-bool-op #{:and :or})

(defn replace-double-vectors [xs]
  (cond
    (and (arity-1-bool-op (first xs))
         (vector? (second xs))
         (vector? (first (second xs))))
    [(first xs) (first (second xs))]

    (and (arity-n-bool-op (first xs))
         (vector? (second xs))
         (vector? (first (second xs))))
    (into [(first xs) (first (second xs))] (nthrest xs 2))))


(defn lhs->eav-syntax
  "Returns vector of LHS expressions in Precept syntax"
  [lhs]
  (when-let [conditions (not-empty lhs)]
    (let [slot->expression-binding (atom {})]
      (reduce
        (fn [acc ast]
          (cond
            ;; "op" is a boolean op expression which may be recursive
            (s/valid? ::precept-event/op-prefixed-condition ast)
            (let [op (first ast)
                  as-lhs (drop-while #(not (map? %)) ast)]
              (conj acc
                (replace-double-vectors
                  (into [op] (lhs->eav-syntax as-lhs)))))

            (s/valid? ::precept-event/accumulator-map ast)
            (let [as-lhs [(:from ast)]
                  binding (symbol (str (name (:result-binding ast))))]
              (conj acc [binding '<- (:accumulator ast) :from (ffirst (lhs->eav-syntax as-lhs))]))

            (s/valid? ::fact-binding-expression ast)
            (let [tuple-ast (select-keys ast [:type :constraints])
                  binding (symbol (str (name (:fact-binding ast))))]
              (conj acc
                [binding '<-
                 (ffirst
                   (parse-as-positional-tuple
                     slot->expression-binding acc tuple-ast))]))

            (s/valid? ::test-condition ast)
            (conj acc (into [:test] (:constraints ast)))

            true
            (parse-as-positional-tuple slot->expression-binding acc ast)))

        []
        conditions))))


(defn matchable-tokens-in-condition
  [condition]
  (reduce
    (fn [acc token]
      (cond
        (vector? token)
        (concat acc (matchable-tokens-in-condition token)) ;;really a vector, not a "token"

        ;; Return all variable bindings in sexprs
        (list? token) ;; could be an accumulator
        (concat acc (filter #(s/valid? ::lang/variable-binding %)
                      token))


       ;; Drop when reserved keywords, symbols
        (#{'_ '<- :from :not :and :or :exists :test} token)
        acc

        true
        (conj acc token)))
    []
    condition))


(defn pattern-matchable-tokens
  "Returns a set of tokens from conditions in Precept's syntax that may have been
  used in a match (either variable bindings or valid values for pattern matching)."
  [conditions]
  (->> conditions
    (mapv
      (fn [condition]
          (matchable-tokens-in-condition condition)))
    (flatten)
    (set)))


;(def lhs
;  '[{:constraints [(= ?bar (:e this))
;                   (number? (:v this))
;                   (= ?foo (:v this))],
;     :type :random-number}
;    {:constraints [(= ?bar (:e this))
;                   (< ?foo 500)],
;     :type :random-number}])
;
;(def accum-ast
;  '{:from {:constraints [], :type :greater-than-500},
;    :result-binding :?eids,
;    :accumulator (precept.accumulators/all :e)})


;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Boolean ops events
;;;;;;;;;;;;;;;;;;;;;;;;;;

;{:bindings {:?v 236, :?e :global},
; :name "precept.app-ns/less-than-500",
; :type :retract-facts-logical,
; :ns-name precept.app-ns,
; :lhs
;           [[:or
;             [:not {:constraints [(= ?e (:e this))], :type :greater-than-500}]
;             [:and
;              {:constraints
;                     [(= ?e (:e this)) (number? (:v this)) (= ?v (:v this))],
;               :type :random-number}
;              {:constraints [(= ?e (:e this)) (< ?v 500)],
;               :type :random-number}]]],
; :event-number 2,
; :matches
; [[{:v 236, :e :global, :t 12, :a :random-number} 14]
;  [{:v 236, :e :global, :t 12, :a :random-number} 15]],
; :id #uuid "049b8c83-fe89-4bf4-bb7b-46ce7991c75c",
; :display-name "less-than-500",
; :state-id #uuid "03dce9e2-bcb9-4d1b-808b-01b3d1869230",
; :facts [{:v true, :e :global, :t 13, :a :less-than-500}],
; :rhs (do (do (do (util/insert! [?e :less-than-500 true])))),
; :state-number 6,
; :props nil}

;{:bindings {:?v 1, :?e #uuid "2164b9f3-c658-4ea2-ba0e-68d52d2b7475"},
; :name "precept.app-ns/less-than-500",
; :type :add-facts-logical,
; :ns-name precept.app-ns,
; :lhs
;  [[:and
;    {:constraints [(= ?e (:e this)) (= ?v (:v this))],
;     :type :random-number}
;    [:not {:constraints [(= ?e (:e this))],
;           :type :greater-than-500}]
;    [:not
;     {:constraints [(= ?v (:v this)) (= :global (:e this))],
;      :type :random-number}]]],
; :event-number 1,
; :matches
; [[{:v 1,
;    :e #uuid "2164b9f3-c658-4ea2-ba0e-68d52d2b7475",
;    :t 8,
;    :a :random-number}
;   13]],
; :id #uuid "5e8857b2-05fc-4c2a-8187-5203ffe1fc7d",
; :display-name "less-than-500",
; :state-id #uuid "a69a765b-d3a9-45d8-b074-10d4923db26e",
; :facts
; ({:v true,
;   :e #uuid "2164b9f3-c658-4ea2-ba0e-68d52d2b7475",
;   :t 9,
;   :a :less-than-500}),
; :rhs (do (do (do (util/insert! [?e :less-than-500 true])))),
; :state-number 3,
; :props nil}

;'{:bindings
;  {:?my-fact {:v 695, :e :global, :t 16, :a :random-number},
;   :?v 695},
;  :name "precept.app-ns/global-greater-than-500",
;  :type :add-facts-logical,
;  :ns-name precept.app-ns,
;  :lhs [{:constraints [(= ?v (:v this)) (= :global (:e this))],
;         :type :random-number,
;         :fact-binding :?my-fact}
;        {:constraints [(> ?v 500)]},] ;; test expression
;  :event-number 3,
;  :matches [[{:v 695, :e :global, :t 16, :a :random-number} 8]],
;  :id #uuid "a1670e1d-ae71-4be5-85e8-607965079c91",
;  :display-name "global-greater-than-500",
;  :state-id #uuid "8411b772-5fcd-43bb-a6ef-baefdd4f80ab",
;  :facts ({:v true, :e :report, :t 18, :a :global-greater-than-500}),
;  :rhs
;               (do (do (do (util/insert! [:report :global-greater-than-500 true])))),
;  :state-number 6,
;  :props nil}


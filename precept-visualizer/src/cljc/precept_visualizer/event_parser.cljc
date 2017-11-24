(ns precept-visualizer.event-parser
  (:require [clojure.spec.alpha :as s]
            [precept.spec.lang :as lang]
            [precept.spec.event :as precept-event]))


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
             (= (s/valid? ::lang/variable-binding (second sexpr))))))


;; Assumes max depth 1 of sexprs in conditions
(defn ast->positional
  "Parses a single condition of Clara's LHS ast into Precept syntax. Should be called in order of LHS declaration.
  `slot->expression-binding` - atom {} of user-defined variable binding for each :e :a :v slot
   `condition` - Clara ast of condition to be parsed.
                 keys are :type (i.e. type of fact, corresponding to attribute)
                 and :constraints (a list, in Clara syntax)
  `eav-kw` - Keyword of the slot to parse
  "
  [slot->expression-binding condition eav-kw]
  (let [ast->binding-symbols (reduce
                               (fn [acc condition]
                                 (if (binding-declaration? condition eav-kw)
                                   (let [eav-binding (eav-kw eav-this-map)
                                         variable-binding (first-variable-binding condition)]
                                     (when variable-binding
                                       (swap! slot->expression-binding assoc eav-kw variable-binding))
                                     (assoc acc
                                       (list '= variable-binding eav-binding) variable-binding
                                       eav-binding variable-binding))
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

      (and (nil? bound-variable-sym)
        (= eav-kw :e))
      '_

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


(defn lhs->eav-syntax
  "Returns a vector of expressions"
  [lhs]
  (when-let [conditions (not-empty lhs)]
    (let [slot->expression-binding (atom {})]
      (reduce
        (fn [acc ast]
          (cond
            (s/valid? ::precept-event/op-prefixed-condition ast)
            (println "Not implemented" ast)

            (s/valid? ::precept-event/accumulator-map ast)
            (let [as-lhs [(:from ast)]
                  binding (symbol (str (name (:result-binding ast))))]
              (conj acc [binding '<- (:accumulator ast) :from (ffirst (lhs->eav-syntax as-lhs))]))

            true
            (let [e (ast->positional
                      slot->expression-binding ast :e)
                  a (:type ast)
                  v (ast->positional
                      slot->expression-binding ast :v)]
              (conj acc [(filterv #(not (nil? %)) [e a v])]))))

        []
        conditions))))


(defn filter-valid-tokens
  [condition]
  (reduce
    (fn [acc token]
      (cond
        (#{'_ '<- :from} token)
        acc

        ;; Return all variable bindings in sexprs
        (list? token) ;; could be an accumulator
        (concat acc (filter #(s/valid? ::lang/variable-binding %)
                      token))

        ;; Variable binding symbols or a literal value
        true
        (conj acc token)))
    []
    condition))


(defn pattern-matchable-tokens
  "Returns a set of tokens from conditions in Precept's syntax that may have been
  used in a match (either variable bindings or valid values for pattern matching)."
  [conditions]
  (->> conditions
    (mapv (fn [condition]
            (if (vector? (first condition))
              (filter-valid-tokens (first condition))
              (filter-valid-tokens condition))))
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

(ns precept-visualizer.event-parser
  (:require [clojure.spec.alpha :as s]
            [precept.spec.lang :as lang]
            [precept.spec.event :as precept-event]))
            ;[precept-visualizer.util :as util]))

(declare ast->datomic-maps)

(s/def ::e any?)
(s/def ::a keyword?)
(s/def ::v any?)
(s/def ::t number?)
(s/def ::tuple-map (s/keys :req-un [::e ::a ::v ::t]))

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


(defn has-attribute-of-ignored-fact? [tuple]
  (= (:a tuple) :precept.spec.sub/request))

(defn trim-uuid [uuid]
  (subs (str uuid) 0 8))

(defmulti trim-uuid-in-fact
          (fn [fact]
            (cond
              (map? fact) :map
              (vector? fact) :vector
              ;; TODO. clj/cljs
              true (println "Unsupported trim uuid type" (type fact)))))

(defmethod trim-uuid-in-fact :vector [fact]
  (into [(trim-uuid (first fact))] (rest fact)))

(defmethod trim-uuid-in-fact :map [fact]
  (update fact :db/id trim-uuid))


(defn prettify-all-vector [facts {:keys [trim-uuids?] :as options}]
  (clojure.walk/postwalk
    (fn [x]
      (cond
        ;; For the diff view it seems we'd want to preserve this attribute,
        ;; so the only place we want to drop it is when explaining subscriptions
        ;(and (map? x) (= (:a x) :precept.spec.sub/response))
        ;(:v x)

        (and (map? x) (every? #{:e :a :v :t} (keys x)))
        (let [eav-vector (display-eav x)]
          (if (and trim-uuids? (uuid? (first eav-vector)))
            (trim-uuid-in-fact eav-vector)
            eav-vector))

        true x))
    facts))

(defn prettify-all-map [facts {:keys [trim-uuids? one-to-many] :as options}]
  (when (not (set? one-to-many))) (ex-info
                                   "prettify-all-facts with :map option
                                   requires :one-to-many option to be a set"
                                   {})
  (ast->datomic-maps facts one-to-many {:trim-uuids? true}))

;; TODO. Would this be better as a multimethod? Seems like it would reduce
;; checking the options every iteration to just once without losing expressivity
(defn prettify-all-facts
  ([facts]
   (prettify-all-facts facts {:trim-uuids? false :format :vector}))
  ([facts {:keys [trim-uuids? format one-to-many] :as options}]
   (if (= format :map)
     (prettify-all-map facts options)
     (prettify-all-vector facts options))))

;; Pretty sparse definition for [e a v] ...
(defn coll-of-eav-vectors? [x] (and (vector? x)
                                    (= (count x) 3)
                                    (keyword? (second x))))

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

(defn ast->binding-symbols [eav-kw slot->expression-binding condition]
  (reduce
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
    (:constraints condition)))

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
  (let [ast->binding-syms (ast->binding-symbols eav-kw slot->expression-binding condition)
        bound-variable-sym (first (vals ast->binding-syms))
        with-symbols-injected (clojure.walk/postwalk-replace
                                ast->binding-syms
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
  (if (= :precept.spec.sub/request (:type ast)) ;; Omit sub request conditions
    acc
    (let [e (ast->positional
              slot->expression-binding ast :e)
          a (:type ast)
          v (ast->positional
              slot->expression-binding ast :v)]
      (conj acc [(filterv #(not (nil? %)) [e a v])]))))


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
  "Returns a seq of tokens from a condition that may have been matched on."
  [condition]
  (reduce
    (fn [acc token]
      (cond
        (vector? token)
        (concat acc (matchable-tokens-in-condition token)) ;; Recur on the vector to iterate over tokens

        ;; Return all variable bindings in sexprs
        (list? token) ;; could be an accumulator
        (concat acc (filter #(s/valid? ::lang/variable-binding %)
                      token))


       ;; Drop when reserved keywords, symbols
        ;; TODO. Precept #110 wrt :all keyword
        (#{'_ '<- :from :not :and :or :exists :test :all} token)
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


(defn reduce-entity-group
  "Takes a collection of :db/id maps with same entity id and returns
  a single map for the entity with one-to-many attributes in collections."
  [group one-to-many]
  (reduce
    (fn [acc m]
      (into {}
        ;; It's easier (for me) to reason over 1 accumulated map for the entity,
        ;; so we map over the kvs of the current map in the group
        ;; with the accumulated value for the entity so far and add the new
        ;; values for the current kvs to the total accumulation
        (map
          (fn [[k v]]
            (cond
              ;;TODO. Initialization of one-to-many
              ;; values to vectors might have been
              ;; handled by this point. Verify this
              (and (contains? one-to-many k)
                (not (contains? acc k)))
              (assoc acc k (vector v))

              (and (contains? one-to-many k)
                (contains? acc k))
              (update acc k conj v)

              true
              (assoc acc k v)))
          m)))
    {}
    group))


(defn ast->datomic-maps
  "Converts :e :a :v :t maps (or coll of them) to :db/id map (or coll of them)
  Returns values of attributes in the one-to-many arg set as collections."
  [facts one-to-many {:keys [trim-uuids?] :as options :or {}}]
  (->> facts
    (clojure.walk/postwalk
      (fn [x]
        (cond
          ;; single map case
          (and (map? x) (every? #{:e :a :v :t} (keys x)))
          ;; ensure value is a vector here if one to many. May be only pass
          ;; we get and want to represent accurately
          (let [value (if (contains? one-to-many (:a x))
                        (vector (:v x))
                        (:v x))]
            {:db/id (if trim-uuids? (trim-uuid (:e x)) (:e x))
             (:a x) value})

          (and (coll? x) (every? #(contains? % :db/id) x))
          ;; Each id (k) in col, return a map with that k as :db/id
          ;; plus the reduction of its group (v)
          ;; respecting one-to-many relationships
          (reduce-kv
            (fn [acc-xs id group]
              (let [entity-map (reduce-entity-group group one-to-many)]
                (conj acc-xs entity-map)))
            []
            (group-by :db/id x))

          true
          x)))))

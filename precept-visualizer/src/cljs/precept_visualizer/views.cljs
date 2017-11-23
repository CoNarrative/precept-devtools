(ns precept-visualizer.views
  (:require [reagent.core :as r]
            [precept.core :as core]
            [precept.spec.event :as precept-event]
            [precept.spec.lang :as lang]
            [precept-visualizer.util :as util]
            [cljs.spec.alpha :as s]))


(defn display-eav [m] ((juxt :e :a :v) m))

(defn mk-colors [n]
  (-> (.-chroma js/window)
    (.scale "Spectral")
    (.colors n)))

(defn pprint-str->edn [s]
  (-> s
    (cljs.reader/read-string)
    (display-eav)
    (str)))

(defn first-variable-binding [condition]
  (some #(when (clojure.string/starts-with? (str %) "?") %)
    condition))

(def eav-this-map
  {:e '(:e this) :a '(:a this) :v '(:v this)})

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

(defn eav-conditions->colors
  "Zips ast-parsed conditions with bindings to return map of variable bindings and value matches to colors"
  [conditions bindings]
  (let [tokens (pattern-matchable-tokens conditions)
        colors (mk-colors (count tokens))
        m (zipmap tokens colors)]
    (reduce
      (fn [acc [variable-kw capture]]
        (if-let [color (get m (symbol (name variable-kw)))]
          (assoc acc capture color)))
      m
      bindings)))

(defn render-diff [];added-strs removed-strs]
  (let [;added (map cljs.reader/read-string added-strs)
        ;removed (map cljs.reader/read-string removed-strs)
        added [{:e 1 :a :foo :v 2 :t 3}]
        removed [{:e 1 :a :foo :v 1 :t 4}]
        ; Reduce into m with :added, :removed, :replaced [[added removed]] and :added
        ; :removed facts not in replaced
        e-a #(juxt :e :a %)
        _ (reduce
            (fn [acc cur]
              (let [replaced
                    (->> (:removed cur)
                      (map
                        (fn [removed-fact]
                          (reduce
                            (fn [acc added-fact]
                              (if (= (e-a removed-fact) (e-a added-fact))
                                (conj acc [added-fact removed-fact])
                                acc))
                            []
                            (:added cur)))))]))
            {}
            {:added added :removed removed})]))

    ;[add-diff remove-diff common] (clojure.data/diff added removed)
    ;(if (every? #{:e :a} (keys common))
    ;  (mapv :v [add-diff remove-diff])
    ;  false))


(defn rule-item [rule]
  [:div (:display-name rule)])

(defn rule-list [rules]
  (let [user-rules (sort-by :type (remove util/impl-rule? rules))
        impl-rules (sort-by :type (filter util/impl-rule? rules))]
    [:div {:style {:display "flex" :flex-direction "column"}}
     (for [rule user-rules]
       ^{:key (:id rule)} [rule-item (util/display-text rule)])]))

(defn state-tree [*orm-states]
  (let [sub @(core/subscribe [:state-tree])
        tree (get @*orm-states (:state/number sub))
        _ (println (count @*orm-states))
        _ (println (:state/number sub))
        _ (println "state-tree render" tree)]
    [:div
     [:h4 "State tree"]
     [:div {:style {:display "flex" :justify-content "space-between"}}
      [:div "e"]
      [:div "a"]
      [:div "v"]]
     (map (fn [[e av]]
            ^{:key (str e)}
            [:div {:style {:display "flex"}}
             [:div {:style {:margin-right 15}}
              (str e)]
             [:div {:style {:display "flex" :flex 1 :flex-direction "column"}}
              (map
                (fn [[a v]]
                  ^{:key (str e "-" a "-" (hash v))}
                  [:div {:style {:min-width "100%" :display "flex" :justify-content "space-between"}}
                   [:div {:style {:flex 1}}
                    (str a)]
                   [:div {:style {:flex 1}}
                    (with-out-str (cljs.pprint/pprint v))]])
                av)]])
       tree)]))

(def event-types->display
  {:add-facts "Insert unconditional"
   :add-facts-logical "Insert logical"
   :retract-facts "Retract"
   :retract-facts-logical "Removed by truth maintenance"})

(def op->display
  {:and "And"
   :or "Or"
   :not "Not exists"
   :exists "There exists"})

(def eav-kw->display
  {:e "entity id"
   :a "attribute"
   :v "value"})

(defn explanation-action [{:keys [event fact-str]}]
  (let [{:keys [state-number event-number facts type]} event
        {:keys [schema/activated schema/caused-by-insert schema/conflict schema/index-path]}
        event
        fact-edn (cljs.reader/read-string fact-str)]
    [:div {:class "example"
           :style {:display "flex"
                   :flex-direction "column"}}
     [:div {:style {:margin-bottom "20px"
                    :display "flex"
                    :justify-content "space-between"}}
      [:div {:class "label black"}
       (str "State " state-number)]
      [:div {:class "label outline black"}
       (str "Event " event-number)]]
     [:div
      [:span {:class "label badge error"}
        (event-types->display type)]]
     [:pre (str (display-eav (first (filter #{fact-edn} facts))))]
     (when activated
       (if caused-by-insert
         [:div (str "Caused by insert " (display-eav caused-by-insert)
                    " at index path " index-path)]
         [:div
           [:div {:class "label tag"}
            (str "Replaced")]
           [:pre (str (display-eav conflict))]

           [:div
            [:span {:class "label badge focus"}
              (str "Enforced schema rules")]
            [:br]

            [:div {:class "label tag"}
             "Attribute"]
            [:pre (str (last index-path))]

            [:div {:class "label tag"}
             "Cardinality:"]
            [:pre (str (first index-path))]]]))]))

(defn most-contrast-text-color
  "Returns black or white, whichever has most contrast given a color"
  [color]
  (if (> (-> (.chroma js/window color)
            (.luminance))
         0.179)
    "#000000"
    "#ffffff"))

(defn display-condition-value [colors slot]
  "Returns colorized markup for a value in a condition if one exists in the
  provided color map, otherwise returns uncolored markup"
  (if-let [color (get colors slot)]
    [:span {:key slot
            :style {:background-color color
                    :color (most-contrast-text-color color)}}

     (str slot)]
    [:span {:key slot} (str slot)]))

(defn highlight-match-in-sexpr [sexpr colors]
  [:span
   "("
   (interpose " "
     (map (fn [sym] (display-condition-value colors sym))
       sexpr))
   ")"])

(defn vector-condition-highlight [eav colors]
  [:span
   "[["
   (interpose " "
     (map (fn [slot]
           (if (list? slot)
             ^{:key slot} [highlight-match-in-sexpr slot colors]
             (display-condition-value colors slot)))
       eav))
   "]]"
   [:br]])

(defn fact-binding-highlight [eav colors]
  [:span
   "["
   (interpose " "
     (map (fn [slot]
            (if (list? slot)
              ^{:key slot} [highlight-match-in-sexpr slot colors]
              (display-condition-value colors slot)))
       eav))
   "]"
   [:br]])

(defn pattern-highlight
  "Returns markup for a rule's conditions or facts, displaying each on a new line."
  [eavs colors]
  [:pre
    (for [eav eavs]
      (cond
        (vector? (first eav))
        ^{:key eav} [vector-condition-highlight (first eav) colors]

        (s/valid? ::lang/accum-expr (first eav))
        ^{:key eav} [fact-binding-highlight eav colors]

        true
        [:span {:key eav}
         "["
         (interpose " "
           (map (fn [slot]
                 (if (list? slot)
                   ^{:key slot} [highlight-match-in-sexpr slot colors]
                   (display-condition-value colors slot)))
             eav))
         "]"
         [:br]]))])

(defn explanation [{:keys [event fact-str] :as payload}]
  (let [{:keys [lhs bindings name type matches display-name rhs state-number event-number
                facts props ns-name]} event
        eav-conditions (lhs->eav-syntax lhs)
        colors (eav-conditions->colors eav-conditions bindings)]
    (cond
      (nil? event)
      nil

      (#{:add-facts :retract-facts} (:type event))
      ^{:key (:fact-str payload)} [explanation-action payload]

      :default
      [:div {:class "example"
             :style {:display "flex"
                     :flex-direction "column"}}
       [:div {:style {:margin-bottom "20px"
                      :display "flex"
                      :justify-content "space-between"}}
         [:span {:class "label black"}
          (str "State " state-number)]
         [:span {:class "label outline black"}
          (str "Event " event-number)]]
       [:div {:style {:margin-bottom "20px"}}
        [:span {:class "label focus outline"}
         "Rule"]
        [:kbd (str name)]]
       [:div
        [:div
          [:span {:class "label tag"}
           "Conditions"]
          [pattern-highlight eav-conditions colors]]
        [:div
         [:span {:class "label tag"}
          (if (> (count matches) 1)
            "Matches"
            "Match")]
         (for [match (distinct (mapv #(if (map? (first %))
                                        (display-eav (first %))
                                        (first %))
                                    matches))]
           ^{:key (str match)} [pattern-highlight
                                [match]
                                colors])]
        [:div
          [:span {:class "label badge error"}
           (event-types->display type)]
          [pattern-highlight [(display-eav (first facts))] colors]]]])))

(defn fact [fact-str]
  [:div {:on-click #(core/then [:transient :explanation/request fact-str])}
   [:pre {:style {:cursor "pointer"}}
    [:span "["
     (interpose " "
       (map (fn [x] [:span {:key (str fact-str "-" x)} (str x)])
           (display-eav (cljs.reader/read-string fact-str))))
     "]"]]])

(defn diff-view []
 (let [{:state/keys [added removed]} @(core/subscribe [:diff-view])]
   [:div
     [:h1 "Diff"]
     [:h3 "Added"]
     (if (empty? added)
       [:div "None"]
       (for [fact-str added]
         ^{:key fact-str} [fact fact-str]))
     [:h3 "Removed"]
     (if (empty? removed)
       [:div "None"]
       (for [fact-str removed]
        ^{:key fact-str} [fact fact-str]))]))

(defn explanations []
  (let [{:keys [payload]} @(core/subscribe [:explanations])]
    (if (empty? payload)
      nil
      [:div {:style {:position "fixed"
                     :overflow-y "scroll"
                     :top 0
                     :width "50vw"
                     :height "100%"
                     :right 0
                     :background "#eee"}}
       [:div {:style {:display "flex" :flex-direction "column"}}
        [:h1 {:style {:align-self "center"}}
         "Explanations"]
        [:button {:on-click #(core/then [:transient :clear-all-explanations true])}
         "Clear all"]
        [:div {:style {:height "25px"}}]
        [:div {:style {:display "flex" :flex-direction "row"}}
         [:div {:style {:min-width "15px"}}]
         [:div {:style {:width "100%" :display "flex" :flex-direction "column"}}
           (for [x payload]
             [:div {:key (:fact-str x)
                    :style {:margin "15px 0px"}}
              [explanation x]])]
         [:div {:style {:min-width "15px"}}]]]])))

(defn change-state-number! [f state-number]
  (core/then [[:global :tracking/sync? false]
              [:global :tracking/state-number (f state-number)]]))

(defn header []
  (let [{:keys [tracking/state-number tracking/sync?
                max-state-number]} @(core/subscribe [:header])]
    [:div {:style {:display "flex" :flex-direction "column"}}
     [:div {:style {:display "flex" :align-items "center"}}
       [:button
        {:class "button"
         :on-click #(change-state-number! dec state-number)
         :disabled (= state-number 0)}
        [:div {:class "large monospace"}
         "-"]]
       [:h3
        {:class "end"
         :style {:margin "0px 15px"}}
        (str "State " state-number)]
       [:button
        {:class "button"
         :on-click #(change-state-number! inc state-number)
         :disabled (= max-state-number state-number)}
        [:div {:class "large monospace"}
         "+"]]]
     [:div
       [:div {:class "form-item"}
        [:label {:class "checkbox"}
         [:input {:type "checkbox"
                  :checked sync?
                  :on-change #(core/then [:global :tracking/sync? (not sync?)])}]
         "Sync"]]]
     [:input {:type "range"
              :style {:padding 0}
              :min 0
              :max max-state-number
              :value state-number
              :on-change #(core/then [[:global :tracking/sync? false]
                                      [:global :tracking/state-number
                                       (-> % .-target .-value js/Number)]])}]]))

(defn main-container [{:keys [rules store]}]
  [:div
   [header]
   [diff-view]
   [explanations]
   [:h4 "Rules"]
   [rule-list (vals @rules)]
   [state-tree store]])

(ns precept-visualizer.views
  (:require [reagent.core :as r]
            [precept.core :as core]
            [precept.spec.event :as precept-event]
            [precept-visualizer.util :as util]
            [cljs.spec.alpha :as s]))

(defn m->vec [m] ((juxt :e :a :v :t) m))

(defn pprint-str->edn [s]
  (-> s
    (cljs.reader/read-string)
    (m->vec)
    (cljs.pprint/pprint)
    (with-out-str)))

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
              (subs (str e) 0 6)]
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
   :retract-facts-logical "Truth maintenance"})

(def op->display
  {:not "Not exists"})

(def eav-kw->display
  {:e "entity id"
   :a "attribute"
   :v "value"})

(defn explanation-action [{:keys [event fact-str]}]
  (let [{:keys [state-number event-number facts type]} event
        {:keys [schema/activated schema/caused-by-insert schema/conflict schema/index-path]}
        event
        fact-edn (cljs.reader/read-string fact-str)]
    [:div {:style {:display "flex" :flex-direction "column"}}
     [:div {:style {:display "flex" :justify-content "space-between"}}
      [:div (str "State " state-number)]
      [:div (str "Event " event-number)]]
     [:div (event-types->display type)]
     [:div (str (m->vec (first (filter #{fact-edn} facts))))]
     (when activated
       (if caused-by-insert
         [:div (str "Caused by insert " (m->vec caused-by-insert)
                    " at index path " index-path)]
         [:div
           [:div (str "Replaced: " (m->vec conflict))]
           [:div (str "Schema rule: " index-path)]]))]))

(defn acc-fn->display [[fq-acc-fn kw]]
  (let [acc-fn-str (last (clojure.string/split (str fq-acc-fn) #"/"))]
    [acc-fn-str (eav-kw->display kw)]))



(defn display-accumulator [condition]
  (let [[acc-fn eav-label] (->> condition
                             :accumulator
                             (vec)
                             (acc-fn->display))]
    (clojure.string/join " "
      (filter some?
        [(clojure.string/capitalize acc-fn)
         (:type (:from condition))
         (when eav-label
           (str eav-label "s"))
         (when-let [constraints (not-empty (:constraints (:from condition)))]
           (str "where " constraints))
         (when-let [binding (:result-binding condition)]
           (str "as " (name binding)))]))))

(defn explanation [{:keys [event fact-str] :as payload}]
  (let [{:keys [lhs bindings name type matches display-name rhs state-number event-number
                facts props ns-name]} event
        _ (println "Event" event)]
    (cond
      (nil? event)
      nil

      (#{:add-facts :retract-facts} (:type event))
      ^{:key (:fact-str payload)} [explanation-action payload]

      :default
      [:div {:style {:display "flex" :flex-direction "column"}}
       [:div {:style {:display "flex" :justify-content "space-between"}}
         [:div (str "State " state-number)]
         [:div (str "Event " event-number)]]
       [:div
        [:div (event-types->display type)]
        [:pre (str (m->vec (first facts)))]]
       [:div "Rule: " (str display-name)]
       [:div "Conditions: "
        [:ol
         (for [{:keys [type fact-binding constraints from accumulator] :as condition} lhs]
             (cond
               (s/valid? ::precept-event/op-prefixed-condition condition)
               [:li {:key (str condition)}
                (clojure.string/join " "
                  [(str (op->display (first condition)))
                   (str (:type (second condition)))
                   (when-let [constr (not-empty (:constraints (second condition)))]
                     (str "where " constr))])]

               (s/valid? ::precept-event/accumulator-map condition)
               [:li {:key (str condition)}
                (do (.log js/console condition)
                  (str (display-accumulator condition)))]

               true
               [:li {:key (str condition)}
                 (clojure.string/join " "
                   [(str (:type condition))
                    (when-let [constr (not-empty (:constraints condition))]
                      (str "where " constr))])]))]
        [:div
         ;; Accumulator matches are a vector of a value and a token. Skip rendering them
         ;; until figure out whether/how to parse
         (when (every? map? matches)
           (if (> (count matches) 1)
             "Matched facts: "
             "Matched fact:")
           (for [match matches]
             [:div {:key (str match)}
               (str (m->vec (first match)))]))]
        [:div "RHS: " (str rhs)]
        [:div "Captures: "
         (for [capture bindings]
           (do
             (println (str capture))
             [:div {:key (str capture)}
              (clojure.string/join ": "
                [(subs (str (first capture)) 1)
                 (str (if (nil? (second capture)) "nil" (second capture)))])]))]]])))
         ;    (if from
         ;      [:div "FROM" (str from)]
         ;      [:div "accumulator" (str accumulator)
         ;        [:li {:key constraints}
         ;         (str "type " type)
         ;         (some->> fact-binding #(str "with fact-binding "))]
         ;       " where " (str constraints)]))
         ;[:div "of rule " (str display-name)]
         ;[:div "in namespace "  (str ns-name)]
         ;[:div "matched the fact " (str matches)]
         ;[:div "and the rule executed " (str rhs)]
         ;[:div "with " (subs (str (ffirst bindings)) 1)
         ; " bound to " (str (second (first bindings)))]]]])))

(defn fact [fact-str]
  [:div {:on-click #(core/then [:transient :explanation/request fact-str])}
   [:pre (pprint-str->edn fact-str)]])

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
        [:button {:style {:background "#fff"
                          :align-self "flex-end"
                          :border "1px solid black"
                          :padding "4px 8px"
                          :border-radius "5px"}
                  :on-click #(core/then [:transient :clear-all-explanations true])}
         "Clear all"]
        [:div {:style {:height "25px"}}]
        [:div {:style {:display "flex" :flex-direction "row"}}
         [:div {:style {:min-width "15px"}}]
         [:div {:style {:display "flex" :flex-direction "column"}}
           (for [x payload]
             [:div {:key (:fact-str x)
                    :style {:margin "15px 0px"}}
              [explanation x]])]
         [:div {:style {:min-width "15px"}}]]]])))

(defn header []
  (let [{:keys [tracking/state-number tracking/sync?
                max-state-number]} @(core/subscribe [:header])]
    [:div {:style {:display "flex"}}
     [:button
      {:on-click
                 #(core/then [[:global :tracking/sync? false]
                              [:global :tracking/state-number (dec state-number)]])
       :disabled (= state-number 0)}
      "-"]
     [:div (str "State: " state-number)]
     [:button
      {:on-click
       #(core/then [[:global :tracking/sync? false]
                    [:global :tracking/state-number (inc state-number)]])
       :disabled (= max-state-number state-number)}
      "+"]
     [:input {:type "checkbox"
              :checked sync?
              :on-change #(core/then [:global :tracking/sync? (not sync?)])}]
     [:div "Sync mode"]
     [:input {:type "range"
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



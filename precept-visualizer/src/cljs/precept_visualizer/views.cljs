(ns precept-visualizer.views
  (:require [reagent.core :as r]
            [precept.core :as core]
            [precept-visualizer.util :as util]))


(defn rule-item [rule]
  [:div (:display-name rule)])

(defn rule-list [rules]
  (let [user-rules (sort-by :type (remove util/impl-rule? rules))
        impl-rules (sort-by :type (filter util/impl-rule? rules))]
    [:div {:style {:display "flex" :flex-direction "column"}}
     (for [rule user-rules]
       ^{:key (:id rule)} [rule-item (util/display-text rule)])]))

(defn state-tree [store]
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
                  (str v)]])
              av)]])
     store)])

(defn explanation []
  (let [{:keys [payload] :as rs} @(core/subscribe [:explanation])
        explanation (first payload)
        conditions (:explanation/conditions explanation)
        bindings (first (:explanation/bindings explanation))
        rule (-> explanation :explanation/rule first)]
    (if (nil? payload) nil
      [:div [:pre (with-out-str (cljs.pprint/pprint payload))]
       [:div (str (:explain/request explanation)
               ; TODO. op-type
               " was inserted because the conditions ")]
       [:div
         (for [{:keys [condition/type condition/fact-binding condition/constraints]} conditions]
           [:div {:key constraints}
            [:ol
             [:li (str "type " type)
              (some->> fact-binding #(str "with fact-binding "))
              " where " constraints]]])]
       [:div "of rule " (-> rule :rule/display-name)]
       [:div "in namespace " (-> rule :rule/ns)]
       [:div "matched " (-> explanation :explanation/matched-fact)]
       [:div "and the rule executed " (-> rule :rule/rhs)]
       [:div "with " (subs (-> bindings :binding/variable) 1)
        " bound to " (-> bindings :binding/value)]])))

(defn diff-view []
 (let [{:keys [state/added state/removed]} @(core/subscribe [:diff-view])]
   [:div
     [:h1 "Diff"]
     [:h3 "Added"]
     (for [fact-str added]
       [:div {:key fact-str
              :on-click #(core/then [(random-uuid) :explain/request fact-str])}
        [:code (str fact-str)]
        [explanation]])
     [:h3 "Removed"]
     (for [fact-str removed]
      [:div {:key fact-str}
       [:code (str fact-str)]])]))


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
     [:div "Sync mode"]]))

(defn main-container [{:keys [rules store] :as precept-state}]
  [:div
   [header]
   [diff-view]
   [:h4 "Rules"]
   [rule-list (vals @rules)]
   [state-tree @store]])



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

(defn explanation-action [payload]
  [:div {:style {:display "flex" :flex-direction "column"}}
   [:div {:style {:display "flex" :justify-content "space-between"}}
    [:div (str "State " (:explanation/state-number payload))]
    [:div (str "Event " (:explanation/event-number payload))]]
   [:div (str (:explanation/fact-str payload)
           " was " (:explanation/event-type payload) " unconditionally ")]])


(defn explanation [payload]
  (let [;{:keys [payload] :as rs} @(core/subscribe [:explanation])
        explanation payload
        conditions (:explanation/conditions explanation)
        bindings (first (:explanation/bindings explanation))
        rule (-> explanation :explanation/rule first)]
    (cond
      (nil? payload) nil
      (:explanation/action payload) [explanation-action payload]
      :default
      [:div {:style {:display "flex" :flex-direction "column"}}
        ;[:div [:pre (with-out-str (cljs.pprint/pprint payload))]
       [:div {:style {:display "flex" :justify-content "space-between"}}
         [:div (str "State " (:explanation/state-number explanation))]
         [:div (str "Event " (:explanation/event-number explanation))]]
       [:div (str (:explanation/fact-str explanation)
               " was " (:explanation/event-type explanation) " because the conditions ")]
       [:div
         (for [{:keys [condition/type condition/fact-binding condition/constraints]} conditions]
           [:div {:key constraints}
            [:ol
             [:li (str "type " type)
              (some->> fact-binding #(str "with fact-binding "))
              " where " constraints]]])]
       [:div "of rule " (-> rule :rule/display-name)]
       [:div "in namespace " (-> rule :rule/ns)]
       [:div "matched the fact " (-> explanation :explanation/matched-fact)]
       [:div "and the rule executed " (-> rule :rule/rhs)]
       [:div "with " (subs (-> bindings :binding/variable) 1)
        " bound to " (-> bindings :binding/value)]])))

(defn fact [fact-str]
  [:div {:on-click #(core/then {:db/id (random-uuid)
                                :explaining/fact fact-str})}
                                ;:explaining/state-number })}
    [:code (str fact-str)]])
          ;[explanation]}])

(defn diff-view []
 (let [{:keys [state/added state/removed]} @(core/subscribe [:diff-view])]
   [:div
     [:h1 "Diff"]
     [:h3 "Added"]
     (for [fact-str added]
       [:div {:key fact-str
              :on-click #(core/then {:db/id (random-uuid)
                                     :explaining/fact fact-str})}
                                     ;:explaining/state-number })}
        [:code (str fact-str)]])
        ;[explanation]])
     [:h3 "Removed"]
     (if (empty? removed)
       [:div "None"]
       (for [fact-str removed]
        [:div {:key fact-str}
         [:code (str fact-str)]]))]))

(defn explanations []
  (let [{:keys [payload]} @(core/subscribe [:explanations])]
    (if (empty? payload) nil
      [:div {:style {:position "absolute"
                     :top 0
                     :width "25vw"
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
             ^{:key (:db/id x)} [explanation x])]
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

(defn main-container [{:keys [rules store] :as precept-state}]
  [:div
   [header]
   [diff-view]
   [explanations]
   [:h4 "Rules"]
   [rule-list (vals @rules)]
   [state-tree @store]])



(ns precept-visualizer.views.explanations
  (:require [precept.core :as precept]
            [precept-visualizer.util :as util]
            [precept-visualizer.event-parser :as event-parser]
            [precept-visualizer.matching :as matching]
            [precept-visualizer.views.consequents :as conseq]))


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
       (util/event-types->display type)]]
     [:pre (str (util/display-eav (first (filter #{fact-edn} facts))))]
     (when activated
       (if caused-by-insert
         [:div (str "Caused by insert " (util/display-eav caused-by-insert)
                 " at index path " index-path)]
         [:div
          [:div {:class "label tag"}
           (str "Replaced")]
          [:pre (str (util/display-eav conflict))]

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


(defn explanation [{:keys [event fact-str] :as payload}]
  (let [{:keys [lhs bindings name type matches display-name rhs state-number event-number
                facts props ns-name]} event
        eav-conditions (event-parser/lhs->eav-syntax lhs)
        colors (matching/eav-conditions->colors eav-conditions bindings)]
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
         [matching/pattern-highlight eav-conditions colors]]
        [:div
         [:span {:class "label tag"}
          (if (> (count matches) 1)
            "Matches"
            "Match")]
         [:pre
           (for [match (event-parser/dedupe-matches-into-eavs matches)]
             ^{:key (str match)} [matching/pattern-highlight-fact
                                  match
                                  colors])]]
        [:div
         [:span {:class "label badge error"}
          (util/event-types->display type)]
         [matching/pattern-highlight
          [(util/display-eav (first facts))] colors]]]])))


(defn explanations []
  (let [{:keys [payload]} @(precept/subscribe [:explanations])]
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
        [:button {:on-click #(conseq/explanations-cleared)}
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

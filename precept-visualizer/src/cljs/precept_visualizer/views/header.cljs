(ns precept-visualizer.views.header
  (:require [precept.core :as precept]
            [precept-visualizer.views.consequents :as conseq]))


(defn header []
  (let [{:keys [tracking/state-number tracking/sync?
                max-state-number]} @(precept/subscribe [:header])]
    [:div {:style {:display "flex" :flex-direction "column"}}
     [:div {:style {:display "flex" :align-items "center"}}
      [:button
       {:class "button"
        :on-click #(conseq/tracking-state-number (dec state-number))
        :disabled (= state-number 0)}
       [:div {:class "large monospace"}
        "-"]]
      [:h3
       {:class "end"
        :style {:margin "0px 15px"}}
       (str "State " state-number)]
      [:button
       {:class "button"
        :on-click #(conseq/tracking-state-number (inc state-number))
        :disabled (= max-state-number state-number)}
       [:div {:class "large monospace"}
        "+"]]]
     [:div
      [:div {:class "form-item"}
       [:label {:class "checkbox"}
        [:input {:type "checkbox"
                 :checked sync?
                 :on-change #(conseq/tracking-state-synced? (not sync?))}]
        "Sync"]]]
     [:input {:type "range"
              :style {:padding 0}
              :min 0
              :max max-state-number
              :value state-number
              :on-change #(conseq/tracking-state-number (-> % .-target .-value js/Number))}]]))

(ns precept-visualizer.views.header
  (:require [precept.core :as precept]
            [precept-visualizer.views.consequents :as conseq]
            [precept-visualizer.themes :as themes]
            [precept-visualizer.views.topbar :as topbar]
            [reagent.core :as r]))


(defn state-controls []
  (let [{:keys [tracking/state-number tracking/sync?
                max-state-number]} @(precept/subscribe [:header])]
    [:div {:style {:display "flex"
                       :flex-direction "column"
                       :background-color "grey"}}
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


(defn header []
  (let [settings (precept/subscribe [:settings])
        theme (precept/subscribe [:selected-theme])]
    (fn []
      [:div
       [:div {:style {:display "flex"}}
        [:div {:style {:padding-left 18
                       :padding-right 12}}
         [:img {:style {:width 18 :height 18 :vertical-align "middle" :margin-right 12}
                :src "svg/precept-icon.svg"}]
         [:strong {:style {:vertical-align "middle"}}
          "Precept Devtools"]]
        [topbar/menu-header topbar/menu-data settings theme]]
       [:div
        [state-controls]]])))

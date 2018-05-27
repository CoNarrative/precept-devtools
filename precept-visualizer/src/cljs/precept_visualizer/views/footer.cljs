(ns precept-visualizer.views.footer
  (:require [precept-visualizer.views.consequents :as conseq]
            [precept-visualizer.icons :as icons]
            [precept.core :as precept]))

(defn icon-button [{:keys [style on-click disabled? icon]}]
  [:div {:style    style
         :on-click #(when-not disabled? (on-click %))}
   icon])

(defn footer [theme]
  (let [{:keys [tracking/state-number tracking/sync?
                max-state-number]} @(precept/subscribe [:header])
        next-state? (not= max-state-number state-number)
        prev-state? (not= state-number 0)]
    [:div {:style {:display          "flex"
                   :flex-direction   "column"
                   :background-color "grey"
                   :border-radius 10
                   :padding "0px 12px"}}
     [:input {:type      "range"
              :style     {:padding 0}
              :min       0
              :max       max-state-number
              :value     state-number
              :on-change #(conseq/tracking-state-number (-> % .-target .-value js/Number))}]
     [:div {:style {:display "flex" :align-items "center" :justify-content "space-between"}}
      [:div {:style {:display "flex"}}
       [:label {:style {:color (:text-color theme)}}
        [:input {:type      "checkbox"
                 :checked   sync?
                 :on-change #(conseq/tracking-state-synced? (not sync?))}]
        "Sync"]]
      [:div {:style {:display "flex" :align-items "center"}}
       [icon-button
        {:on-click  #(conseq/tracking-state-number (dec state-number))
         :disabled? (not prev-state?)
         :icon      [icons/arrow-circle-left
                     {:style (if prev-state?
                               {:cursor "pointer"}
                               {:cursor "pointer"
                                :color (:disabled-text-color theme)})}]}]
       [:strong {:style {:margin "0px 15px"}}
        (str "Fire rules #" state-number)]
       [icon-button
        {:on-click  #(conseq/tracking-state-number (inc state-number))
         :disabled? (not next-state?)
         :icon      [icons/arrow-circle-right
                     {:style (if next-state?
                               {:cursor "pointer"}
                               {:cursor "pointer"
                                :color (:disabled-text-color theme)})}]}]]]]))

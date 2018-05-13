(ns precept-visualizer.views.tabs
  (:require [precept-visualizer.views.consequents :as conseq]))

(def tabs-config [{:label "Event log" :key :event-log}
                  {:label "Actions" :key :actions}
                  {:label "Subscriptions" :key :subscriptions}
                  {:label "Diff" :key :diff}
                  {:label "Rules" :key :rules}
                  {:label "Session state" :key :state}])

(defn tab [{:keys [label on-click selected? theme border-right border-bottom]}]
  [:div {:style    {:display      "flex" :align-items "center" :justify-content "center" :width "100%"
                    :border-right border-right :border-bottom border-bottom :border-top "1px solid grey"
                    :background   (if selected? (:background-color theme) (:disabled-text-color theme))}
         :on-click on-click}
   [:div {:style {:color (if selected? (:text-color theme) (:inverted-text-color theme))}}
    label]])

(defn tabs [{:keys [selected-tab theme]}]
  (let [num-tabs (count tabs-config)]
    [:div {:style {:display         "flex"
                   :justify-content "space-around"
                   :height          54 :margin-bottom 30}}
     (map-indexed (fn [i {:keys [key] :as tab-config}]
                    (let [selected? (= selected-tab key)]
                      [tab (merge tab-config
                                  {:border-right  (if (not= (dec num-tabs) i) "1px solid grey" "inherit")
                                   :border-bottom (if selected? "inherit" "1px solid grey")
                                   :on-click      #(conseq/selected-tab key)
                                   :selected?     selected?
                                   :theme         theme})]))
                  tabs-config)]))

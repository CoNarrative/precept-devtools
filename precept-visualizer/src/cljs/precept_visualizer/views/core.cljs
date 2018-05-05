(ns precept-visualizer.views.core
  (:require [precept.core :as precept]
            [precept-visualizer.views.header :as header]
            [precept-visualizer.views.diff :as diff]
            [precept-visualizer.views.explanations :as explanations]
            [precept-visualizer.views.rule-list :as rule-list]
            [precept-visualizer.views.state-tree :as state-tree]
            [precept-visualizer.views.subscriptions :as subs]
            [precept-visualizer.views.actions :as actions]
            [precept-visualizer.views.event-log :as event-log]
            [precept-visualizer.views.footer :as footer]
            [reagent.core :as r]))

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

(defn tabs [{:keys [tabs-config *selected-tab theme]}]
  (let [num-tabs (count tabs-config)
        selected-tab @*selected-tab]
    [:div {:style {:display         "flex"
                   :justify-content "space-around"
                   :height          54 :margin-bottom 30}}
     (map-indexed (fn [i {:keys [key] :as tab-config}]
                    (let [selected? (= selected-tab key)]
                      [tab (merge tab-config
                                  {:border-right  (if (not= (dec num-tabs) i) "1px solid grey" "inherit")
                                   :border-bottom (if selected? "inherit" "1px solid grey")
                                   :on-click      #(reset! *selected-tab key)
                                   :selected?     selected?
                                   :theme         theme})]))
                  tabs-config)]))

(defn main [{:keys [rules store]}]
  (let [*selected-tab (r/atom :diff)]
    (fn []
      (let [windows (first (:payload @(precept/subscribe [:windows])))
            theme (:theme @(precept/subscribe [:selected-theme]))
            schemas @(precept/subscribe [:schemas])
            selected-tab @*selected-tab]
        [:div {:style {:display        "flex"
                       :flex-direction "column"
                       :min-height "100vh"
                       :color          (:text-color theme)
                       :background     (:background-color theme)}}
         [header/header theme]
         [:div {:style {:display "flex"}}
          [:div {:style {:display        "flex"
                         :flex-direction "column"
                         :margin-bottom (when (:state-controls/visible? windows)
                                          (:state-controls/height windows))
                         :width          (str (or (:main/width-percent windows) "100") "vw")}}
           [tabs {:tabs-config   tabs-config
                  :*selected-tab *selected-tab
                  :theme         theme}]
           [:div {:style {:margin "0px 20px"}}
            (case selected-tab
              :event-log
              [event-log/event-log theme]
              :actions
              [actions/actions-list]
              :subscriptions
              [subs/subscriptions-list store]
              :diff
              [diff/diff-view theme schemas]
              :rules
              [rule-list/rule-list rules theme]
              :state
              [state-tree/main store theme]
              [:div "Not found"])]
           (when (:state-controls/visible? windows)
             [:div {:style {:position "fixed" :bottom "0"
                            :height (:state-controls/height windows)
                            :width    (str (or (:main/width-percent windows) "100") "vw")}}
              [footer/footer]])]
          [explanations/fact-list theme windows]]]))))

;;TODO. Make a test for converting these to :db/id maps
(comment
  (def facts
    '[{:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :random-number, :v 642, :t 7}
      {:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :greater-than-500, :v true, :t 8}
      {:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :greater-than-500, :v true, :t 8}
      {:e #uuid "aaa3ebe1-856c-41e8-80d5-6f82d98c9d0b", :a :precept.spec.sub/response, :v {:facts [[:entity>500 ({:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :random-number, :v 642, :t 7} {:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :greater-than-500, :v true, :t 8})]]}, :t 11}
      {:e :report, :a :entity>500, :v ({:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :random-number, :v 642, :t 7} {:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :greater-than-500, :v true, :t 8}), :t 10}]))


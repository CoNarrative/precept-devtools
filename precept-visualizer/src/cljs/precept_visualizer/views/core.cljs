(ns precept-visualizer.views.core
  (:require [precept.core :as precept]
            [precept-visualizer.views.header :as header]
            [precept-visualizer.views.diff :as diff]
            [precept-visualizer.views.explanations :as explanations]
            [precept-visualizer.views.rule-list :as rule-list]
            [precept-visualizer.views.state-tree :as state-tree]))


(defn main [{:keys [rules store]}]
  (let [windows (first (:payload @(precept/subscribe [:windows])))
        theme (:theme @(precept/subscribe [:selected-theme]))
        schemas @(precept/subscribe [:schemas])]
    [:div {:style {:display "flex"
                   :flex-direction "column"
                   :color (:text-color theme)
                   :background (:background-color theme)}}
     [header/header]
     [:div {:style {:display "flex"}}
      [:div {:style {:display "flex"
                     :flex-direction "column"
                     :width (str (or (:main/width-percent windows) "100") "vw")}}
       [diff/diff-view theme schemas]
       [:h4 "Rules"]
       [rule-list/rule-list (vals @rules)]
       [state-tree/main store]]
      [explanations/explanations theme windows]]]))

;;TODO. Make a test for converting these to :db/id maps
(comment
  (def facts
    '
    [{:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :random-number, :v 642, :t 7}
     {:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :greater-than-500, :v true, :t 8}
     {:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :greater-than-500, :v true, :t 8}
     {:e #uuid "aaa3ebe1-856c-41e8-80d5-6f82d98c9d0b", :a :precept.spec.sub/response, :v {:facts [[:entity>500 ({:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :random-number, :v 642, :t 7} {:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :greater-than-500, :v true, :t 8})]]}, :t 11}
     {:e :report, :a :entity>500, :v ({:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :random-number, :v 642, :t 7} {:e #uuid "159d97a6-6947-4433-b21f-45d84cc78755", :a :greater-than-500, :v true, :t 8}), :t 10}]))


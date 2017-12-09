(ns precept-visualizer.views.core
  (:require [precept.core :as precept]
            [precept-visualizer.views.header :as header]
            [precept-visualizer.views.diff :as diff]
            [precept-visualizer.views.explanations :as explanations]
            [precept-visualizer.views.rule-list :as rule-list]
            [precept-visualizer.views.state-tree :as state-tree]))


(defn main [{:keys [rules store]}]
  (let [windows (first (:payload @(precept/subscribe [:windows])))]
    [:div {:style {:display "flex" :flex-direction "column"}}
     [header/header]
     [:div {:style {:display "flex"}}
      [:div {:style {:display "flex"
                     :flex-direction "column"
                     :width (str (or (:main/width-percent windows) "100") "vw")}}
       [diff/diff-view]
       [:h4 "Rules"]
       [rule-list/rule-list (vals @rules)]
       [state-tree/main store]]
      [explanations/explanations]]]))

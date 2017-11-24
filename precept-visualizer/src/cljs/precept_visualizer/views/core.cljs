(ns precept-visualizer.views.core
  (:require [precept-visualizer.views.header :as header]
            [precept-visualizer.views.diff :as diff]
            [precept-visualizer.views.explanations :as explanations]
            [precept-visualizer.views.rule-list :as rule-list]
            [precept-visualizer.views.state-tree :as state-tree]
            [precept-visualizer.util :as util]))


(defn main [{:keys [rules store]}]
  [:div
   [header/header]
   [diff/diff-view]
   [explanations/explanations]
   [:h4 "Rules"]
   [rule-list/rule-list (vals @rules)]
   [state-tree/main store]])

(ns precept-visualizer.views.rule-list
  (:require [precept-visualizer.util :as util]))


(defn rule-item [rule]
  [:div (:display-name rule)])


(defn rule-list [rules]
  (let [user-rules (sort-by :type (remove util/impl-rule? rules))
        impl-rules (sort-by :type (filter util/impl-rule? rules))]
    [:div {:style {:display "flex" :flex-direction "column"}}
     (for [rule user-rules]
       ^{:key (:id rule)} [rule-item (util/display-text rule)])]))


(ns precept-visualizer.views.actions
  (:require [reagent.core :as r]
            [precept.core :as precept]
            [precept-visualizer.event-parser :as event-parser]))

(defn actions-list []
  (let [{:keys [results]} @(precept/subscribe [:actions])
        fact-format (:fact-format @(precept/subscribe [:settings]))
        formatted-results (event-parser/prettify-all-facts results {:trim-uuids? true :format fact-format})]
    [:div
     (for [x formatted-results]
       [:pre {:key (str x)}
        (str x)])]))

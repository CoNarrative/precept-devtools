(ns precept-visualizer.views.consequents
  (:require [precept.core :as core :refer [then]]))


(defn tracking-state-number [state-number]
  (then [[:global :tracking/sync? false]
         [:global :tracking/state-number state-number]]))

(defn tracking-state-synced? [bool]
  (then [:global :tracking/sync? bool]))

(defn fact-explanation-requested [fact-str]
  (then [:transient :explanation/request fact-str]))

(defn explanations-cleared []
  (then [:transient :clear-all-explanations true]))

(defn stop-explain-fact-requested [fact-str]
  (then [:transient :stop-explain-fact-requested fact-str]))

(defn theme-is [theme-id]
  (then [:settings :settings/selected-theme-id theme-id]))

(defn fact-format-is [kw]
  (then [:settings :settings/fact-format kw]))

(defn viewing-rule-history [name show?]
  (println "show name" show? name)
  (if show?
    (then [:transient :rule-history/request name])
    (then [:transient :rule-history/clear-request name])))
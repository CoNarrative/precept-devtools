(ns precept-visualizer.views.consequents
  (:require [precept.core :as core :refer [then]]))


(defn tracking-state-number [state-number]
  (then [[:global :tracking/sync? false]
         [:global :tracking/state-number state-number]]))

(defn tracking-state-synced? [bool]
  (then [:global :tracking/sync? bool]))

(defn fact-explanation-requested [fact-edn-map]
  (let [{:keys [e a v t]} fact-edn-map]
    (then
      {:db/id :transient
       :fact-tracker.request/fact-e (str e) ;; FIXME. all session fact/e, fact/a come as strs
       :fact-tracker.request/fact-a (str a)
       :fact-tracker.request/fact-t t})))

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

(defn viewing-rule-history-event [rule-name event-index]
  (then {:db/id :transient
         :rule-history.view-event-request/rule-name rule-name
         :rule-history.view-event-request/event-index event-index}))

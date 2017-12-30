(ns precept-visualizer.rules.rule-tracking
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept-visualizer.event-parser :as event-parser]
            [precept.accumulators :as acc]
            [precept-visualizer.ws :as ws]))


;; FIXME. When the session is defined in another namespace, rules in this ns that use this fn
;; don't get its definition. Hoping this is fixed by https://github.com/cerner/clara-rules/issues/359,
;; https://github.com/CoNarrative/precept/issues/111
(defn sort-rule-history-tracker-events [history-event-entities]
  (-> history-event-entities
      (->> (clojure.walk/postwalk (fn [x] (if (record? x) (into {} x) x))))
      (event-parser/ast->datomic-maps #{} {:trim-uuids? false})
      (->> (reduce concat))
      (->> (sort
             (fn [a b] (if (> (:rule-history.event/state-number a)
                              (:rule-history.event/state-number b))
                         1
                         (if (not= (:rule-history.event/state-number a)
                                   (:rule-history.event/state-number b))
                           -1
                           (if (> (:rule-history.event/event-number a)
                                  (:rule-history.event/event-number b))
                             1
                             -1))))))))


(rule on-clear-rule-history-requested
  {:group :action}
  [[:transient :rule-history/clear-request ?rule-name]]
  [?rule-history <- [_ :rule-history/rule-name ?rule-name]]
  =>
  (retract! ?rule-history))


(rule on-view-rule-history-requested
  {:group :action}
  [[:transient :rule-history/request ?rule-name]]
  =>
  (println "Rule history requested for rule name " ?rule-name)
  (insert-unconditional! {:db/id (util/guid)
                          :rule-history/rule-name ?rule-name
                          :rule-history/selected-event-index 0}))


(rule on-view-rule-history-event-by-index
  {:group :action}
  [[:transient :rule-history.view-event-request/rule-name ?rule-name]]
  [[:transient :rule-history.view-event-request/event-index ?event-index]]
  [[?rule-history :rule-history/rule-name ?rule-name]]
  =>
  (insert-unconditional! [?rule-history :rule-history/selected-event-index ?event-index]))


(rule history-entry-when-rule-history-for-rule
  [[?rule-history :rule-history/rule-name ?rule-name]]

  [[?rule-id :rule/display-name ?rule-name]]
  [[?event-id :event/rule ?rule-id]]
  [[?event-id :event/number ?event-number]]
  [[?state-id :state/events ?event-id]]
  [[?state-id :state/number ?state-number]]
  =>
  (let [rule-event-eid (util/guid)]
    (println "History event for rule exists at "
             {:rule-name ?rule-name
              :event-id ?event-id
              :history-event-id rule-event-eid
              :state-number ?state-number
              :event-number ?event-number})
    (insert! [[?rule-history :rule-history/event-ids rule-event-eid]
              [rule-event-eid :rule-history.event/event-id ?event-id]
              [rule-event-eid :rule-history.event/state-number ?state-number]
              [rule-event-eid :rule-history.event/event-number ?event-number]])))


(rule sorted-rule-events-when-tracking-history
  [[?rule-history :rule-history/rule-name ?rule-name]]
  [[?rule-history :rule-history/event-ids ?rule-history-event]]
  [[?rule-history-event :rule-history.event/event-id ?event-id]]
  [(<- ?rule-history-event-meta (entity ?rule-history-event))]
  =>
  (println "Rule history event meta" ?rule-history-event-meta)
  (insert! [?rule-history :rule-history/event-meta ?rule-history-event-meta]))


(rule rule-history-events-in-order
  [[?rule-history :rule-history/rule-name]]
  [?history-events <- (acc/all :v) :from [?rule-history :rule-history/event-meta]]
  =>
  (println "History events" ?history-events)
  (insert! [?rule-history :rule-history/sorted-event-maps (sort-rule-history-tracker-events ?history-events)]))


(define [?rule-history :rule-history/total-event-count (count ?sorted-maps)]
        :- [[?rule-history :rule-history/sorted-event-maps ?sorted-maps]])


(rule selected-history-meta-when-specified-rule-history-event-index
  [[?rule-history :rule-history/selected-event-index ?selected-event-index]]
  [[?rule-history :rule-history/sorted-event-maps ?sorted-events]]
  =>
  (when (not (empty? ?sorted-events))
    (let [selected-event (nth ?sorted-events ?selected-event-index)]
      (insert! {:db/id ?rule-history
                :rule-history/selected-state-number (:rule-history.event/state-number selected-event)
                :rule-history/selected-event-number (:rule-history.event/event-number  selected-event)
                :rule-history/selected-event-id (:rule-history.event/event-id selected-event)}))))


(rule fetch-log-entry-if-not-exists-when-rule-history-selected
  [[?rule-history :rule-history/selected-event-id ?event-id]]
  [:not [?event-id :event/log-entry]]
  [[?rule-history :rule-history/selected-state-number ?state-number]]
  [[?rule-history :rule-history/selected-event-number ?event-number]]
  =>
  (ws/get-log-entry-by-coords [?state-number ?event-number])
  (println "Selected rule history state event for rule" ?rule-history ?state-number ?event-number))


(rule rule-history-selected-log-entry
  [[?rule-history :rule-history/selected-event-id ?event-id]]
  [[?event-id :event/log-entry ?log-entry]]
  =>
  (println "Inserting log entry for rule history" ?event-id ?log-entry)
  (insert! [?rule-history :rule-history/selected-log-entry ?log-entry]))


(define [?e :rule-history/sub {:name ?rule-name
                               :log-entry ?log-entry
                               :selected-event-index ?selected-event-index
                               :total-event-count ?total-event-count}]
        :- [[?e :rule-history/rule-name ?rule-name]]
        [[?e :rule-history/selected-log-entry ?log-entry]]
        [[?e :rule-history/total-event-count ?total-event-count]]
        [[?e :rule-history/selected-event-index ?selected-event-index]])


(defsub :rule-history
  [?subs <- (acc/all :v) :from [_ :rule-history/sub]]
  =>
  {:subs ?subs})


(ns precept-visualizer.rules.fact-tracking
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept.accumulators :as acc]
            [precept-visualizer.ws :as ws]
            [precept-visualizer.event-parser :as event-parser]))


;; See if can declare and have def in rules.core
(defn sort-fact-tracker-occurrences [history-event-entities]
  (-> history-event-entities
      (->> (clojure.walk/postwalk (fn [x] (if (record? x) (into {} x) x))))
      (event-parser/ast->datomic-maps #{} {:trim-uuids? false})
      (->> (reduce concat))
      (->> (sort
             (fn [a b] (if (> (:fact-tracker.occurrence/state-number a)
                              (:fact-tracker.occurrence/state-number b))
                         1
                         (if (not= (:fact-tracker.occurrence/state-number a)
                                   (:fact-tracker.occurrence/state-number b))
                           -1
                           (if (> (:fact-tracker.occurrence/event-number a)
                                  (:fact-tracker.occurrence/event-number b))
                             1
                             -1))))))))


(rule on-track-fact-request-when-no-tracker-for-fact-identity
  {:group :action}
  [[:transient :fact-tracker.request/fact-e ?e]]
  [[:transient :fact-tracker.request/fact-a ?a]]
  [[:transient :fact-tracker.request/fact-t ?t]]
  [:not [:and [_ :fact-tracker/fact-e ?e]
              [_ :fact-tracker/fact-a ?a]]]
  =>
  (println "no tracker, creating" ?e ?a ?t)
  (let [fact-tracker-id (util/guid)
        viewer-id (util/guid)]
    ;; FIXME. EAV of fact/e etc are strings...
    (insert-unconditional! [[fact-tracker-id :fact-tracker/fact-e ?e]
                            [fact-tracker-id :fact-tracker/fact-a ?a]
                            [fact-tracker-id :fact-tracker/viewer-ids viewer-id]
                            [viewer-id :fact-tracker.viewer/fact-t ?t]])))

(rule on-track-fact-request-when-existing-tracker-for-fact-identity
  {:group :action}
  [[:transient :fact-tracker.request/fact-e ?e]]
  [[:transient :fact-tracker.request/fact-a ?a]]
  [[:transient :fact-tracker.request/fact-t ?t]]
  [[?tracker-id :fact-tracker/fact-e ?e]]
  [[?tracker-id :fact-tracker/fact-a ?a]]
  [[?tracker-id :fact-tracker/viewer-ids ?viewer-id]]
  [:not [?viewer-id :fact-tracker.viewer/fact-t ?t]]
  =>
  (let [new-viewer-id (util/guid)]
    (println "existing tracker. adding another fact-t" ?e ?a ?t)
    (insert-unconditional! [[?tracker-id :fact-tracker/viewer-ids new-viewer-id]
                            [new-viewer-id :fact-tracker.viewer/fact-t ?t]])))


(rule fetch-log-entry-if-not-exists-for-selected-event
  [[?viewer-id :fact-tracker.viewer/selected-event-id ?event-id]]
  [:not [?event-id :event/log-entry]]
  [[?viewer-id :fact-tracker.viewer/selected-state-number ?state-number]]
  [[?viewer-id :fact-tracker.viewer/selected-event-number ?event-number]]
  =>
  (ws/get-log-entry-by-coords [?state-number ?event-number])
  (println "Selected fact history for viewer at " ?viewer-id ?state-number ?event-number))


(rule viewer-has-selected-log-entry
  [[?viewer-id :fact-tracker.viewer/selected-event-id ?event-id]]
  [[?event-id :event/log-entry ?log-entry]]
  =>
  (println "selected log entry for viewer" ?log-entry)
  (insert! [?viewer-id :fact-tracker.viewer/selected-log-entry ?log-entry]))

(rule tracker-has-event-meta-for-every-event-where-fact-it-tracks-occurs
  [[?fact-tracker :fact-tracker/fact-e ?fact-e]]
  [[?fact-tracker :fact-tracker/fact-a ?fact-a]]
  [[?fact-id :fact/e ?fact-e]]
  [[?fact-id :fact/a ?fact-a]]
  [[?fact-id :fact/t ?fact-t]]
  [[?event-id :event/facts ?fact-id]]
  [[?event-id :event/number ?event-number]]
  [[?state-id :state/events ?event-id]]
  [[?state-id :state/number ?state-number]]
  =>
  (println "Added tracker occurrence" ?fact-e ?fact-a ?fact-id)
  (let [occurrence-id (util/guid)]
    (insert! [[?fact-tracker :fact-tracker/occurrence-ids occurrence-id]
              [occurrence-id :fact-tracker.occurrence/event-id ?event-id]
              [occurrence-id :fact-tracker.occurrence/state-number ?state-number]
              [occurrence-id :fact-tracker.occurrence/event-number ?event-number]
              [occurrence-id :fact-tracker.occurrence/fact-t ?fact-t]])))

(rule tracker-has-sorted-occurrences-1
 [[?fact-tracker :fact-tracker/occurrence-ids ?occurrence-id]]
 [(<- ?occurrence (entity ?occurrence-id))]
 =>
 (insert! [?fact-tracker :fact-tracker/occurrence-map ?occurrence]))

(rule tracker-has-sorted-occurrences-2
  [[?fact-tracker :fact-tracker/fact-e]]
  [[?fact-tracker :fact-tracker/fact-a]]
  [?occurrences <- (acc/all :v) :from [?fact-tracker :fact-tracker/occurrence-map]]
  =>
  (let [sorted-occurrence-maps (sort-fact-tracker-occurrences ?occurrences)
        _ (println "sorted occurrences " sorted-occurrence-maps)]
    (insert! [[?fact-tracker :fact-tracker/sorted-occurrence-maps sorted-occurrence-maps]
              [?fact-tracker :fact-tracker/total-event-count (count ?occurrences)]])))

(rule when-no-selected-occurrence
  [[:global :tracking/state-number ?state-number]]
  [[?fact-tracker :fact-tracker/sorted-occurrence-maps ?sorted-occurrences]]
  [[?fact-tracker :fact-tracker/viewer-ids ?viewer-id]]
  [:not [?viewer-id :fact-tracker.viewer/selected-event-id]]
  [[?viewer-id :fact-tracker.viewer/fact-t ?fact-t]]
  =>
  (println "Sorted occurrences " ?fact-t ?state-number)
  (when-let [first-occurrence (->> ?sorted-occurrences
                                   (filter #(and (= (:fact-tracker.occurrence/fact-t %)
                                                    ?fact-t)
                                                 (= (:fact-tracker.occurrence/state-number %)
                                                    ?state-number)))
                                   (first))]
    (let [{:fact-tracker.occurrence/keys [event-number state-number event-id]} first-occurrence]
      (insert-unconditional! {:db/id                                     ?viewer-id
                              :fact-tracker.viewer/selected-event-id     event-id
                              :fact-tracker.viewer/selected-event-number event-number
                              :fact-tracker.viewer/selected-state-number state-number}))))

(rule assign-realized-viewers-to-fact-tracker
  [[?fact-tracker :fact-tracker/fact-e]]
  [[?fact-tracker :fact-tracker/fact-a]]
  [[?fact-tracker :fact-tracker/viewer-ids ?viewer-id]]
  [(<- ?viewer (entity ?viewer-id))]
  =>
  (insert! [?fact-tracker :fact-tracker/viewer-subs ?viewer]))

(rule form-fact-tracker-sub
  [[?fact-tracker :fact-tracker/fact-e]]
  [[?fact-tracker :fact-tracker/fact-a]]
  [(<- ?fact-tracker-sub (entity ?fact-tracker))]
  =>
  (insert! [(util/guid) :fact-tracker/sub ?fact-tracker-sub]))

(defsub :fact-trackers
  [?subs <- (acc/all :v) :from [_ :fact-tracker/sub]]
  =>
  {:subs ?subs})

;; TODO. Not sure if we should pull all eids associated with the
;; fact-str or leave them. No apparent problems with leaving them (current approach)
(rule on-clear-explanation
  {:group :action}
  [[:transient :stop-explain-fact-requested ?fact-str]]
  [?fact-exp <- [_ :explaining/fact ?fact-str]]
  =>
  (retract! ?fact-exp))


(rule on-clear-all-explanations
  {:group :action}
  [[:transient :clear-all-explanations true]]
  [?explanations <- (acc/all) :from [_ :explaining/fact]]
  =>
  (retract! ?explanations))


(rule explain-fact-for-events-in-state
  ;; when explaining a fact
  [[?explanation-id :explaining/fact ?fact-str]]
  ;; and we are tracking a state number
  [[:global :tracking/state-number ?current-state-number]]

  ;; and current state has event with that fact's value
  [[?state-id :state/number ?current-state-number]]
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/facts ?fact-id]]
  [[?fact-id :fact/string ?fact-str]]
  [[?event-id :event/number ?event-number]]
  ;; when no log entry exists for a fact mentioned in event of current state
  [:not [?event-id :event/log-entry]]

  =>
  ;; get event object to generate explanation
  (ws/get-log-entry-by-coords [?current-state-number ?event-number]))


(rule form-event-explanation-when-requested-and-log-entry
  [[?explanation-id :explaining/fact ?fact-str]]
  ;; and we are tracking a state number
  [[:global :tracking/state-number ?current-state-number]]

  ;; and current state has event with that fact's value
  [[?state-id :state/number ?current-state-number]]
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/facts ?fact-id]]
  [[?fact-id :fact/string ?fact-str]]
  [[?event-id :event/log-entry ?log-entry]]
  =>
  ;; Because this is already a data representation of an event there's not
  ;; much we can do to make it into an explanation here. View will format as needed
  ;; We are performing this insertion so we can accumulate all explanations
  ;; in a subscription. Logically it's not necessary; we could just have this in the
  ;; sub if we had better matching and marshalling capability with lists
  ;; note :event/log-entry is a base fact, pulled from server on demand and
  ;; kept in memory. This duplicate of it should come and go based upon the
  ;; presence of an explanation request
  (insert! [?explanation-id :explanation/log-entry {:fact-str ?fact-str
                                                    :event ?log-entry}]))


(defsub :explanations
  [?explanations <- (acc/all :v) :from [_ :explanation/log-entry]]
  =>
  {:payload ?explanations})


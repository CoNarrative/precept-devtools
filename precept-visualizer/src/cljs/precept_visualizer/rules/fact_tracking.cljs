(ns precept-visualizer.rules.fact-tracking
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept-visualizer.util :as vis-util]
            [precept.accumulators :as acc]
            [precept-visualizer.ws :as ws]
            [precept-visualizer.event-parser :as event-parser]))


(defn sort-fact-tracker-occurrences [history-event-entities]
  (-> history-event-entities
      (->> (clojure.walk/postwalk vis-util/coerce-record-to-map))
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

(rule on-clear-tracker-request-1
  {:group :action}
  [[:transient :fact-tracker.clear-request/tracker-id ?tracker-id]]
  [?viewer-join <- [?tracker-id :fact-tracker/viewer-ids ?viewer-id]]
  [(<- ?viewer (entity ?viewer-id))]
  =>
  (retract! ?viewer-join)
  (retract! ?viewer))

(rule no-trackers-with-no-viewers
  [[?tracker-id :fact-tracker/fact-e]]
  [:not [?tracker-id :fact-tracker/viewer-ids]]
  [(<- ?tracker (entity ?tracker-id))]
  =>
  (retract! ?tracker))

(rule on-clear-viewer-request
  {:group :action}
  [[:transient :fact-tracker.viewer.clear-request/viewer-id ?viewer-id]]
  [?viewer-join <- [_ :fact-tracker/viewer-ids ?viewer-id]]
  [(<- ?viewer (entity ?viewer-id))]
  =>
  (retract! ?viewer-join)
  (retract! ?viewer))


(rule on-track-fact-request-when-no-tracker-for-fact-identity
  {:group :action}
  [[:transient :fact-tracker.request/fact-e ?e]]
  [[:transient :fact-tracker.request/fact-a ?a]]
  [[:transient :fact-tracker.request/fact-t ?t]]
  =>
  (insert-unconditional! [:transient :fact-tracker.request/hash (str ?e ?a)]))

(rule on-track-fact-request-when-no-tracker-for-fact-identity-2
  {:group :action}
  [[:transient :fact-tracker.request/hash ?hash]]
  [[:transient :fact-tracker.request/fact-e ?e]]
  [[:transient :fact-tracker.request/fact-a ?a]]
  [[:transient :fact-tracker.request/fact-t ?t]]

  [:not [_ :existing-tracker-hash ?hash]]
  =>
  (let [fact-tracker-id (util/guid)
        viewer-id (util/guid)]
    (insert-unconditional! [[fact-tracker-id :fact-tracker/fact-e ?e]
                            [fact-tracker-id :fact-tracker/fact-a ?a]
                            [fact-tracker-id :fact-tracker/viewer-ids viewer-id]
                            [viewer-id :fact-tracker.viewer/fact-t ?t]])))

(rule on-track-fact-request-when-no-tracker-for-fact-identity-3
  [[?fact-tracker-id :fact-tracker/fact-e ?e]]
  [[?fact-tracker-id :fact-tracker/fact-a ?a]]
  =>
  (insert! [(util/guid) :existing-tracker-hash (str ?e ?a)]))


(rule on-track-fact-request-when-existing-tracker-for-fact-identity
  {:group :action}
  [[:transient :fact-tracker.request/fact-e ?e]]
  [[:transient :fact-tracker.request/fact-a ?a]]
  [[:transient :fact-tracker.request/fact-t ?t]]
  [[?tracker-id :fact-tracker/fact-e ?e]]
  [[?tracker-id :fact-tracker/fact-a ?a]]
  [:not [_ :fact-tracker.viewer/fact-t ?t]]
  =>
  (let [new-viewer-id (util/guid)]
    (insert-unconditional! [[?tracker-id :fact-tracker/viewer-ids new-viewer-id]
                            [new-viewer-id :fact-tracker.viewer/fact-t ?t]])))


(rule on-view-occurrence-request
  {:group :action}
  [[:transient :fact-tracker.viewer.view-occurrence-request/viewer-id ?viewer-id]]
  [[:transient :fact-tracker.viewer.view-occurrence-request/occurrence-index ?occurrence-index]]
  [[?fact-tracker :fact-tracker/viewer-ids ?viewer-id]]
  [[?fact-tracker :fact-tracker/sorted-occurrence-maps ?occurrences]]
  =>
  (let [{:fact-tracker.occurrence/keys [event-id state-number event-number]} (nth ?occurrences ?occurrence-index)]
    (insert-unconditional! {:db/id                                         ?viewer-id
                            :fact-tracker.viewer/selected-occurrence-index ?occurrence-index
                            :fact-tracker.viewer/selected-event-id         event-id
                            :fact-tracker.viewer/selected-state-number     state-number
                            :fact-tracker.viewer/selected-event-number     event-number})))



(rule fetch-log-entry-if-not-exists-for-selected-event
  [[?viewer-id :fact-tracker.viewer/selected-event-id ?event-id]]
  [:not [?event-id :event/log-entry]]
  [[?viewer-id :fact-tracker.viewer/selected-state-number ?state-number]]
  [[?viewer-id :fact-tracker.viewer/selected-event-number ?event-number]]
  =>
  (ws/get-log-entry-by-coords [?state-number ?event-number]))


(rule viewer-has-selected-log-entry
  [[?viewer-id :fact-tracker.viewer/selected-event-id ?event-id]]
  [[?event-id :event/log-entry ?log-entry]]
  =>
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
  (let [sorted-occurrence-maps (sort-fact-tracker-occurrences ?occurrences)]
    (insert! [[?fact-tracker :fact-tracker/sorted-occurrence-maps sorted-occurrence-maps]
              [?fact-tracker :fact-tracker/total-event-count (count ?occurrences)]])))

(rule when-no-selected-occurrence
  [[:global :tracking/state-number ?state-number]]
  [[?fact-tracker :fact-tracker/sorted-occurrence-maps ?sorted-occurrences]]
  [[?fact-tracker :fact-tracker/viewer-ids ?viewer-id]]
  [:not [?viewer-id :fact-tracker.viewer/selected-event-id]]
  [[?viewer-id :fact-tracker.viewer/fact-t ?fact-t]]
  =>
  ;; Gets the first mention of fact instance in the state number that's being globally tracked
  ;; This won't be "what the user selected" if they generated a request from the diff view.
  ;; For that we'd at least need to know whether the op was added or removed since the same fact instance
  ;; can be added and removed in the same state
  (when-let [first-occurrence (->> ?sorted-occurrences
                                   (filter #(and (= (:fact-tracker.occurrence/fact-t %)
                                                    ?fact-t)
                                                 (= (:fact-tracker.occurrence/state-number %)
                                                    ?state-number)))
                                   (first))]
    (let [{:fact-tracker.occurrence/keys [event-number state-number event-id]} first-occurrence
          selected-occurrence-index (.indexOf ?sorted-occurrences first-occurrence)]
      ;; Using insert uncond because selected occurrence index will be set via action
      (insert-unconditional! {:db/id                                     ?viewer-id
                              :fact-tracker.viewer/selected-event-id     event-id
                              :fact-tracker.viewer/selected-event-number event-number
                              :fact-tracker.viewer/selected-state-number state-number
                              :fact-tracker.viewer/selected-occurrence-index selected-occurrence-index}))))

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

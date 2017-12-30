(ns precept-visualizer.rules.core
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept.accumulators :as acc]
            [precept.orm :as orm]
            [precept-visualizer.themes :as themes]
            [precept-visualizer.mouse :as mouse]
            [precept-visualizer.state :as state]
            [precept-visualizer.schema :refer [db-schema client-schema]]
            [precept-visualizer.event-parser :as event-parser]
    ;; TODO. Rename other-rules ns
            [precept-visualizer.rules.rule-tracking]
            [precept-visualizer.other-rules]
            [precept-visualizer.ws :as ws]))


;; FIXME. Duplicate definition in rules.rule-tracking
;; When the session is defined in another namespace, rules in this ns that use this fn
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


(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [{:db/id :global
                           :tracking/sync? true
                           :view/mode :diff}
                          {:db/id :settings
                           :settings/selected-theme-id ::themes/light
                           :settings/fact-format :vector}
                          (merge {:db/id ::themes/dark} themes/dark)
                          (merge {:db/id ::themes/light} themes/light)]))


(rule on-clear-all-explanations
  {:group :action}
  [[:transient :clear-all-explanations true]]
  [?explanations <- (acc/all) :from [_ :explaining/fact]]
  =>
  (retract! ?explanations))


;; TODO. Not sure if we should pull all eids associated with the
;; fact-str or leave them. No apparent problems with leaving them (current approach)
(rule on-clear-explanation
  {:group :action}
  [[:transient :stop-explain-fact-requested ?fact-str]]
  [?fact-exp <- [_ :explaining/fact ?fact-str]]
  =>
  (retract! ?fact-exp))


(rule on-explain-request
  {:group :action}
  [[:transient :explanation/request ?fact-str]]
  [:not [_ :explaining/fact ?fact-str]]
  =>
  (insert-unconditional! [(util/guid) :explaining/fact ?fact-str]))


(rule when-sync-tracking-latest-state
  {:group :action}
  [[:global :tracking/sync? true]]
  [[_ :max-state-number ?max]]
  =>
  (insert-unconditional! [:global :tracking/state-number ?max]))


(define [:global :max-state-number ?n] :- [?n <- (acc/max :v) :from [_ :state/number]])


(define [:windows :explanations/width-percent 0] :- [:not [_ :explaining/fact]])


(define [:windows :explanations/width-percent 50] :- [:exists [_ :explaining/fact]])


(define [:windows :main/width-percent (- 100 ?explanation-width)]
        :- [[:windows :explanations/width-percent ?explanation-width]])



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
  ;; when no explanation exists for a fact mentioned in event of current state
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
  ;; Note: request-id isn't effectively used -- could make inserted fact one-to-many
  ;; and get rid of it
  ;; We are performing this insertion so we can accumulate all explanations
  ;; in a subscription. Logically it's not necessary; we could just have this in the
  ;; sub if we had better matching and marshalling capability with lists
  ;; note :event/log-entry is a base fact, pulled from server on demand and
  ;; kept in memory. This duplicate of it should come and go based upon the
  ;; presence of an explanation request, hence insertL here. :explanation
  ;; implies "active"
  (insert! [?explanation-id :explanation/log-entry {:fact-str ?fact-str
                                                    :event ?log-entry}]))


(defsub :explanations
  [?explanations <- (acc/all :v) :from [_ :explanation/log-entry]]
  =>
  {:payload ?explanations})


(defsub :selected-theme
  [[:settings :settings/selected-theme-id ?theme-id]]
  [?kvs <- (acc/all (juxt :a :v)) :from [?theme-id :all]]
  =>
  {:selected-theme ?theme-id
   :theme (into {} ?kvs)})


(defsub :settings
  [[:settings :settings/selected-theme-id ?theme-id]]
  [[:settings :settings/fact-format ?fact-format]]
  =>
  {:theme-id ?theme-id
   :fact-format ?fact-format})


(defsub :header
  [[_ :tracking/state-number ?n]]
  [[_ :tracking/sync? ?bool]]
  [[_ :max-state-number ?max]]
  =>
  {:tracking/sync? ?bool
   :tracking/state-number ?n
   :max-state-number ?max})


(defsub :diff-view
  [[:global :view/mode :diff]]
  ;; just marshalling for now, nothing derived. Move to upstream rule
  ;; once enriching
  [[_ :tracking/state-number ?n]]
  [[?state :state/number ?n]]
  [[?state :state/added ?added]]
  [[?state :state/removed ?removed]]
  =>
  {:state/added ?added
   :state/removed ?removed})


(defsub :state-tree
  [[_ :tracking/state-number ?n]]
  =>
  {:state/number ?n})


(defsub :windows
  [(<- ?windows (entity :windows))]
  =>
  {:payload ?windows})



(session visualizer-session
  'precept-visualizer.rules.core
  'precept-visualizer.rules.rule-tracking
  'precept-visualizer.other-rules
  :db-schema db-schema
  :client-schema client-schema
  :reload true)



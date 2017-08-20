(ns precept-visualizer.rules
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!]]
            [precept.accumulators :as acc]
            [precept-visualizer.schema :refer [db-schema client-schema]]))

(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [[:global :tracking/sync? true]
                          [:global :view/mode :diff]]))

(rule on-explain-request
  {:group :action}
  [[?request-id :explain/request ?fact-str]]

  ; Match fact id to explain
  [[?fact-id :fact/string ?fact-str]]

  ; Assume for state being actively tracked
  [[_ :tracking/state-number ?state-number]]
  [[?state-id :state/number ?state-number]]

  ; For the state's events that have the requested fact
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/facts ?fact-id]]

  ; Event may or may not have a rule associated. Consider breaking here.
  ; Pull the rule for that event, discard unwanted fields later
  [[?event-id :event/rule ?rule-id]]

  ; Pull its LHS so we can get to the conditions
  [[?rule-id :rule/lhs ?lhs-id]]
  ;[[?lhs-id :lhs/conditions ?condition-ids]]
  [?condition-ids <- (acc/all :v) :from [?lhs-id :lhs/conditions]]
  ; Pull its conditions
  ;[(<- ?conditions (entities ?condition-ids))]

  [?binding-ids <- (acc/all :v) :from [?event-id :event/bindings]]
  ;[[?event-id :event/bindings ?binding-ids]]
  ;[(<- ?bindings (entities ?binding-ids))]
  ; Pull match refs
  ;[?match-ids <- (acc/all :v) :from [?event-id :event/matches]]
  ; Seems like match should be one-to-one but modeled as one-to-many
  [[?event-id :event/matches ?match-id]]
  [[?match-id :match/fact ?matched-fact-id]]
  [[?matched-fact-id :fact/string ?matched-fact-str]]

  [(<- ?rule (entity ?rule-id))]
  =>
  (println "Explain"
    ?fact-id ?state-number ?state-id ?event-id ?rule-id ?rule ?lhs-id ?binding-ids ?condition-ids)
  (insert-unconditional! {:db/id ?request-id
                          :explanation/rule ?rule
                          :explanation/binding-ids ?binding-ids
                          :explanation/condition-ids ?condition-ids
                          :explanation/matched-fact ?matched-fact-str}))

(rule bindings-for-explain-request
  [[?request-id :explain/request]]
  ; TODO. request-id must be declared separately, but should be able to be bound
  ; via accumulator only
  [?binding-ids <- (acc/all :v) :from [?request-id :explanation/binding-ids]]
  [(<- ?bindings (entities ?binding-ids))]
  =>
  (println "[explanation] Bindingss" ?bindings ?binding-ids ?request-id)
  (insert-unconditional! [?request-id :explanation/bindings ?bindings]))

(rule conditions-for-explain-request
  [[?request-id :explain/request]]
  [?condition-ids <- (acc/all :v) :from [?request-id :explanation/condition-ids]]
  [(<- ?conditions (entities ?condition-ids))]
  =>
  (println "[explanation] Conditionss" ?conditions ?condition-ids)
  (insert-unconditional! [?request-id :explanation/conditions ?conditions]))
;
;(rule explain-condition-ids-for-explain-request
;  [[?request-id :explain/condition-ids ?condition-ids]]
;  [[?request-id :explain/response ?m]]
;  (<- ?conditions (entities ?condition-ids))
;  =>
;  (insert-unconditional! [?request-id :explain/response (assoc ?m :conditions ?conditions)]))


(rule print-facts
  [?fact <- [_ :all]]
  =>
  (println "[visualizer] Fact " ?fact))

(rule on-diff-request
  {:group :action}
  [[_ :diff-request]]
  =>
  (println "Got a diff request"))

(rule max-state-number
  [?n <- (acc/max :v) :from [_ :state/number]]
  =>
  (insert! [:global :max-state-number ?n])
  (println "max state number" ?n))

(rule when-sync-tracking-latest-state
  [[:global :tracking/sync? true]]
  [[_ :max-state-number ?max]]
  =>
  (insert-unconditional! [:global :tracking/state-number ?max]))

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

(defsub :explanation
  [[?request-id :explain/request]]
  [?response <- (acc/all) :from [?request-id :all]]
  =>
  {:payload ?response})


(session visualizer-session
  'precept-visualizer.rules
  :db-schema db-schema
  :client-schema client-schema
  :reload true)

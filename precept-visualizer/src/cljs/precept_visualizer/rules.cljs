(ns precept-visualizer.rules
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!]]
            [precept.accumulators :as acc]
            [precept-visualizer.schema :refer [db-schema]])
  (:require-macros [precept.dsl :refer [<- entity entities]]))

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
  [[?lhs-id :lhs/conditions ?condition-ids]]
  ; Pull its conditions
  ;[(<- ?conditions (entities ?condition-ids))]

  [[?event-id :event/bindings ?binding-ids]]
  ;[(<- ?bindings (entities ?binding-ids))]

  [(<- ?rule (entity ?rule-id))]
  =>
  (println "Explain"
    ?fact-id ?state-number ?state-id ?event-id ?rule-id ?rule ?lhs-id)
  (insert-unconditional! {:db/id ?request-id
                          :explain/response ?rule
                          :explain/binding-ids ?binding-ids
                          :explain/condition-ids ?condition-ids}))

;(rule explain-binding-ids-for-explain-request
;  [[?request-id :explain/binding-ids ?binding-ids]]
;  [[?request-id :explain/response ?m]]
;  (<- ?bindings (entities ?binding-ids))
;  =>
;  (insert-unconditional! [?request-id :explain/response (assoc ?m :bindings ?bindings)]))
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
  [[?request-id :explain/response ?response]]
  =>
  {:payload ?response})


(session visualizer-session
  'precept-visualizer.rules
  :db-schema db-schema
  :reload true)

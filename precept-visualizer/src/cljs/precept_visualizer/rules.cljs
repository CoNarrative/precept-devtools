(ns precept-visualizer.rules
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept.accumulators :as acc]
            [precept.orm :as orm]
            [precept-visualizer.state :as state]
            [precept-visualizer.schema :refer [db-schema client-schema]]))

(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [[:global :tracking/sync? true]
                          [:global :view/mode :diff]]))

(rule on-clear-all-explanations
  {:group :action}
  [[:transient :clear-all-explanations true]]
  [?explaining <- (acc/all) :from [_ :explaining/fact]]
  =>
  (retract! ?explaining))

(rule existing-explanation
  [[?request-id :explaining/fact ?fact-str]]
  [[:global :tracking/state-number ?state-number]]
  [[?state-id :state/number ?state-number]]

  ; Match fact id to explain
  [[?fact-id :fact/string ?fact-str]]

  ; For the state's events that have the requested fact
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/facts ?fact-id]]

  ; We have an explanation for this request
  [[?explanation-id :explanation/fact-id ?fact-id]]
  [[?explanation-id :explanation/state-id ?state-id]]
  [[?explanation-id :explanation/event-id ?event-id]]
  =>
  ; Assert the explanation fulfills the request
  (insert-unconditional! [?explanation-id :explanation/request-id ?request-id]))

(rule explain-unexplained-action
  [[?request-id :explaining/fact ?fact-str]]
  [[:global :tracking/state-number ?state-number]]
  [[?state-id :state/number ?state-number]]
  [[?fact-id :fact/string ?fact-str]]
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/facts ?fact-id]]

  ; These should be mutex but just to be sure
  [:not [?event-id :event/rule]]
  [[?event-id :event/action true]]

  ; No explanation exists
  [:not [:and [_ :explanation/fact-id ?fact-id]
              [_ :explanation/state-id ?state-id]
              [_ :explanation/event-id ?event-id]]]

  [[?event-id :event/type ?event-type]]
  [[?event-id :event/number ?event-number]]
  =>
  (insert-unconditional!
    (merge {:db/id (util/guid)}
      {:explanation/request-id ?request-id
       :explanation/state-id ?state-id
       :explanation/event-id ?event-id
       :explanation/fact-id ?fact-id
       :explanation/event-type ?event-type
       :explanation/state-number ?state-number
       :explanation/event-number ?event-number
       :explanation/fact-str ?fact-str
       :explanation/action true})))

(rule on-explain-request
  {:group :action}
  [[?request-id :explaining/fact ?fact-str]]

  ; Assume for state being actively tracked. Need to cover where this is not the case.
  ; Probably requires view to provide arguments.
  [[:global :tracking/state-number ?state-number]]
  [[?state-id :state/number ?state-number]]

  ; Match fact id to explain
  [[?fact-id :fact/string ?fact-str]]

  ; For the state's events that have the requested fact
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/facts ?fact-id]]

  ; No explanation exists
  ; TODO. ?explanation-id should be in first slot but Clara throws previously unbound variable error
  ; Parsing appears correct in tests
  ; Appears because inside :not, can't form new binding
  [:not [:and [_ :explanation/fact-id ?fact-id]
              [_ :explanation/state-id ?state-id]
              [_ :explanation/event-id ?event-id]]]

  ; Event may or may not have a rule associated. Consider breaking here.
  ; Pull the rule for that event, discard unwanted fields later
  [[?event-id :event/rule ?rule-id]]

  ; Pull its LHS so we can get to the conditions
  [[?rule-id :rule/lhs ?lhs-id]]
  [?condition-ids <- (acc/all :v) :from [?lhs-id :lhs/conditions]]

  [?binding-ids <- (acc/all :v) :from [?event-id :event/bindings]]

  ; Pull match refs
  ;[?match-ids <- (acc/all :v) :from [?event-id :event/matches]]
  ; Seems like match should be one-to-one but modeled as one-to-many
  [[?event-id :event/matches ?match-id]]
  [[?match-id :match/fact ?matched-fact-id]]
  [[?matched-fact-id :fact/string ?matched-fact-str]]
  [[?event-id :event/number ?event-number]]
  [[?event-id :event/type ?event-type]]

  [(<- ?rule (entity ?rule-id))]
  =>
  ; Should be possible to insert logical. Waiting to see whether more performant to keep
  ; explanations around or recalculate.
  (insert-unconditional!
    (merge {:db/id (util/guid)}
      {:explanation/request-id ?request-id
       :explanation/state-id ?state-id
       :explanation/event-id ?event-id
       :explanation/fact-id ?fact-id
       :explanation/event-type ?event-type
       :explanation/state-number ?state-number
       :explanation/event-number ?event-number
       :explanation/fact-str ?fact-str
       :explanation/rule ?rule
       :explanation/binding-ids ?binding-ids
       :explanation/condition-ids ?condition-ids
       :explanation/matched-fact ?matched-fact-str})))

(rule bindings-for-explain-request
  [[?explanation-id :explanation/request-id ?request-id]]
  [?binding-ids <- (acc/all :v) :from [?explanation-id :explanation/binding-ids]]
  [(<- ?bindings (entities ?binding-ids))]
  =>
  (insert! [?explanation-id :explanation/bindings ?bindings]))

(rule conditions-for-explain-request
  [[?explanation-id :explanation/request-id ?request-id]]
  [?condition-ids <- (acc/all :v) :from [?explanation-id :explanation/condition-ids]]
  [(<- ?conditions (entities ?condition-ids))]
  =>
  (insert! [?explanation-id :explanation/conditions ?conditions]))

(rule max-state-number
  [?n <- (acc/max :v) :from [_ :state/number]]
  =>
  (insert! [:global :max-state-number ?n]))

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

(defsub :state-tree
  [[_ :tracking/state-number ?n]]
  =>
  {:state/number ?n})

(rule form-explanation
  [[?request-id :explaining/fact]]
  [[?explanation-id :explanation/request-id ?request-id]]
  [?response <- (acc/all) :from [?explanation-id :all]]
  =>
  (insert! [(util/guid) :resolved/explanation ?response]))

(defsub :explanations
  [?explanations <- (acc/all :v) :from [_ :resolved/explanation]]
  =>
  {:payload ?explanations})



(session visualizer-session
  'precept-visualizer.rules
  :db-schema db-schema
  :client-schema client-schema
  :reload true)

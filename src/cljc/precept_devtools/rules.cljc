(ns precept-devtools.rules
  (:require [precept.core :as core]
            [precept.rules :refer [fire-rules rule define session]]
            [precept.dsl :refer [<- entity entities]]
            [precept.accumulators :as acc]
            [precept.repl :as repl]
            ;[precept.util :refer [insert! insert-unconditional! insert]]
            [precept-devtools.schema :refer [db-schema]]))

(rule print-all-facts
  [?fact <- [_ :all]]
  =>
  (println "Fact " ?fact))

(rule matches-for-event-number
  [[_ :ui/matches-for-event-number ?n]]
  [[?e :event/number ?n]]
  [[?e :event/match ?fact-ref]]
  [[?fact-ref :fact/string ?v]]
  =>
  (println ?v))

(comment
  (rule facts-added-for-event-number
    [[_ :ui/facts-added-for-event-number ?n]]
    [[?e :event/number ?n]]
    [:or [?e :event/type :add-facts] [?e :event/type :add-facts-logical]]
    [[?e :event/facts ?fact-ref]]
    [[?fact-ref :fact/string ?v]]
    =>
    (println ?v))

  (rule facts-added-for-state-number
    [[_ :ui/facts-added-for-state-number ?n]]
    [[?e :state/number ?n]]
    [[?e :state/events ?event]]
    [:or [?event :event/type :add-facts] [?event :event/type :add-facts-logical]]
    [[?event :event/facts ?fact-ref]]
    [[?fact-ref :fact/string ?v]]
    =>
    (println ?v))

  (rule rule-that-matched-on-a-fact-for-a-state
    [[_ :ui/rule-matched-on-fact-for-state [?n ?fact-str]]]
    [[?e :state/number ?n]]
    [[?e :state/events ?event]]
    [[?fact-id :fact/string ?fact-str]]
    [[?event :event/matches ?fact-id]]
    [[?event :event/rule ?rule]]
    [(<- ?rule-entity (entity ?rule))]
    =>
    (println ?rule-entity))

  ;; We would not do this in one rule--just getting a feel for the schema
  (rule why-rule-fired-for-state
    [[_ :ui/why-rule-fired-for-state [?n ?rule-name]]]
    [[?e :state/number ?n]]

    ;; This rule should fire once per event in state n
    [[?e :state/events ?event]]

    ;; Hydrate all matches for the event. Note there is no relation
    ;; between matches and a particular rule. We may want both bindings
    ;; and matches for rule and event to locate matches
    ;; for a rule without having to pass through events
    [?match-ids <- (acc/all :v) :from [?event-id :event/matches]]

    ;; For every match id hydrate the fact the match refers to
    ;; Problematically (and likely only because we're trying to do this all in one rule),
    ;; we have a list of refs that we can't match with
    ;[?matched-facts <- ???]

    ;; Here's one way we might support in the future
    ;; [?fact-ids <- (acc/all :v) :from [?match-id :match/fact] :in ?match-ids]
    ;[(<- ?facts (entities ?fact-ids))]
    ;; where the above unsupported syntax would be implemented as:
    ;; 1. Some rule rewrites this one and inserts values of list as individual facts
    ;;    `(doseq [eid ?match-ids]
    ;;       (insert! [(guid) ::rulegen/in-list eid]))
    ;; (rule in-list___gen-impl-1
    ;;   [[?e ::rulegen/in-list ?eid]]
    ;;   ; Copy whatever was written before the `:in` and match on value if
    ;;   ; the accumulator contains `:v`
    ;;   [[?eid :match/fact ?v]]
    ;;   =>
    ;;   (insert! [?e ::rulegen/response ?v])
    ;;
    ;; ...etc, following the pattern established in the `entities` macro

    ;; "Bindings" seem more like a property of an event, not a rule. They
    ;; change from one event to another and rules themselves don't have this concept.
    ;; Rules are scoped to one event, so any bindings for an event will refer to that one rule
    [?binding-ids <- (acc/all :v) :from [?event :event/bindings]]
    [(<- ?bindings (entities ?binding-ids))]

    ;; Rules exist independently and are referenced by events. One rule per event
    [[?event :event/rule ?rule]]

    ;; Without something like "pull" that can identify refs and hydrate them
    ;; as entities we have to get down to the level of values
    ;; before accumulating
    [[?rule :rule/rhs ?rhs]] ;; a value
    [[?rule :rule/lhs ?lhs]] ;; a ref

    [?condition-refs <- (acc/all :v) :from [?lhs :lhs/conditions]]
    [(<- ?conditions (entities ?condition-refs))]
    =>
    (println "Rule" ?rule-name "fired because its conditions")))


(session devtools-session
  'precept-devtools.rules
  :db-schema db-schema)


;(-> my-session)
  ;(insert [[:global :foo :bar]])
  ;(fire-rules))

;(core/start! {:session my-session :facts [[:global :foo :bar]]})

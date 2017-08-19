(ns precept-visualizer.rules
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!]]
            [precept.accumulators :as acc]))

(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [[:global :tracking/sync? true]]))

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

(session visualizer-session
  'precept-visualizer.rules)

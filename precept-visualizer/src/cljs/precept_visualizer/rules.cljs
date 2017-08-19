(ns precept-visualizer.rules
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!]]
            [precept.accumulators :as acc]))

(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [[:global :tracking/sync? true]
                          [:global :view/mode :diff]]))

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


(session visualizer-session
  'precept-visualizer.rules)

(ns precept-visualizer.rules
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert!]]
            [precept.accumulators :as acc]))


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

(rule tracked-state-number
  [:not [_ :ui/requested-state-number]]
  [[:global :max-state-number ?n]]
  =>
  (insert! [:global :tracked-state-number ?n]))

(defsub :header
  [[_ :tracked-state-number ?n]]
  =>
  {:tracked-state-number ?n})

(session visualizer-session
  'precept-visualizer.rules)

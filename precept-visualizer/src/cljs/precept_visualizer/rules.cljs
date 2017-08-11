(ns precept-visualizer.rules
  (:require [precept.rules :refer [rule define session]]))

(rule print-facts
  [?fact <- [_ :all]]
  =>
  (println "[visualizer] Fact " ?fact))

(session visualizer-session
  'precept-visualizer.rules)

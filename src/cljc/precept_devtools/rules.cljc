(ns precept-devtools.rules
  (:require [precept.core :as core]
            [precept.rules :refer [fire-rules rule define session]]
            [precept.util :refer [insert! insert-unconditional! insert]]
            [precept-devtools.schema :refer [db-schema]]))

(rule print-all-facts
  [?fact <- [_ :all]]
  =>
  (println "Fact " ?fact))

(session my-session
  'precept-devtools.rules
  :db-schema db-schema)

;(-> my-session)
  ;(insert [[:global :foo :bar]])
  ;(fire-rules))

;(core/start! {:session my-session :facts [[:global :foo :bar]]})

(ns precept-visualizer.other-rules
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept.accumulators :as acc]))


(rule user-defined-one-to-many-attribute
  [[?e :db/cardinality :db.cardinality/many]]
  [[?e :db/ident ?attr]]
  =>
  (insert! [?e :schemas/one-to-many ?attr]))


(defsub :schemas
  [?vs <- (acc/all :v) :from [_ :schemas/one-to-many]]
  =>
  ;; FIXME. :one-to-many clashes internally with precept.orm. Should
  ;; be part of "use namespaced keywords" issue
  {:one-to-manies (set ?vs)})

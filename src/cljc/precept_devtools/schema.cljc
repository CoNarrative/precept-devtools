(ns precept-devtools.schema
    (:require [precept.schema :refer [attribute]]))


(defn mk-db-schema []
  [
   ;; User
   (attribute :user/id
     :db.type/uuid
     :db/unique :db.unique/identity)

   ;; State
   (attribute :state/id
     :db.type/uuid
     :db/unique :db.unique/value)

   (attribute :state/number
     :db.type/long)

   (attribute :state/event
     :db.type/ref
     :db/unique :db.unique/value
     :db/isComponent true
     :db/cardinality :db.cardinality/many)

   ;; Event
   (attribute :event/number
     :db.type/long)

   (attribute :event/type
     :db.type/keyword)

   (attribute :event/action
     :db.type/boolean)

   (attribute :event/match
     :db.type/ref
     :db/isComponent true
     :db/cardinality :db.cardinality/many)

   (attribute :event/fact
     :db.type/ref
     :db/cardinality :db.cardinality/many)

   (attribute :event/rule
     :db.type/ref)

   ;; Rule
   (attribute :rule/name
     :db.type/string)

   (attribute :rule/ns
     :db.type/string)

   (attribute :rule/display-name
     :db.type/string)

   (attribute :rule/bindings
     :db.type/ref
     :db/isComponent true
     :db/cardinality :db.cardinality/many)

   (attribute :rule/rhs
     :db.type/string)

   (attribute :rule/props
      :db.type/string)

   (attribute :rule/lhs
     :db.type/ref
     :db/cardinality :db.cardinality/many
     :db/isComponent true)

   ;; LHS
   (attribute :lhs/condition
     :db.type/ref
     :db/isComponent true
     :db/cardinality :db.cardinality/many)

   ;; Condition
   (attribute :condition/index
     :db.type/long)

   (attribute :condition/type
     :db.type/keyword)

   (attribute :condition/constraint
     :db.type/string
     :db/cardinality :db.cardinality/many)

   (attribute :condition/fact-binding
      :db.type/keyword)

   ;; Binding
   (attribute :binding/variable
     :db.type/string)

   (attribute :binding/value
     :db.type/string)

   ;; Fact
   (attribute :fact/string
     :db.type/string)

   (attribute :fact/e
     :db.type/string)

   (attribute :fact/a
     :db.type/string)

   (attribute :fact/v
     :db.type/string)

   (attribute :fact/t
     :db.type/long)])


(def db-schema (mk-db-schema))



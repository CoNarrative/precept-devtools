(ns precept-devtools.schema
    (:require [precept.schema :refer [attribute]]))


; Attribute names are pluralized for one-to-many rels and
; singular for one-to-one.
; This informs us of the arity of an attribute.
; Once in the session all facts refer to a single value
; so it can still be confusing, but hopefully less confusing
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

   (attribute :state/events
     :db.type/ref
     :db/unique :db.unique/value
     :db/isComponent true
     :db/cardinality :db.cardinality/many)

   ;; Event
   (attribute :event/type
      :db.type/keyword)

   (attribute :event/number
     :db.type/long)

   (attribute :event/action
     :db.type/boolean)

   (attribute :event/matches
     :db.type/ref
     :db/isComponent true
     :db/cardinality :db.cardinality/many)

   (attribute :event/bindings
      :db.type/ref
      :db/isComponent true
      :db/cardinality :db.cardinality/many)

   (attribute :event/facts
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

   (attribute :rule/rhs
     :db.type/string)

   (attribute :rule/props
      :db.type/string)

   (attribute :rule/lhs
     :db.type/ref
     :db/isComponent true)

   ;; LHS
   (attribute :lhs/conditions
     :db.type/ref
     :db/isComponent true
     :db/cardinality :db.cardinality/many)

   ;; Condition
   (attribute :condition/index
     :db.type/long)

   (attribute :condition/type
     :db.type/keyword)

   (attribute :condition/constraints
     :db.type/string
     :db/cardinality :db.cardinality/many)

   (attribute :condition/fact-binding
      :db.type/keyword)

   ;; Binding
   (attribute :binding/variable
     :db.type/string)

   (attribute :binding/value
     :db.type/string)

   ;; Match - joins event and fact. Facts are standalone
   ;; entities that are frequently referenced, so choosing
   ;; not to resolve directly to a fact value of some kind
   (attribute :match/fact
     :db.type/ref)

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



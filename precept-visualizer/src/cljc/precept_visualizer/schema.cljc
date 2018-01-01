(ns precept-visualizer.schema
   (:require [precept.schema :refer [attribute]]))


; Attribute names are pluralized for one-to-many rels and
; singular for one-to-one.
; This informs us of the arity of an attribute.
; Once in the session all facts refer to a single value
; so it can still be confusing, but hopefully less confusing
(defn mk-db-schema []
  [
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
     :db/isComponent true
     :db/cardinality :db.cardinality/many)

   ;(attribute :state/added
   ;   :db.type/ref
   ;   :db/isComponent true
   ;  :db/cardinality :db.cardinality/many)
   ;
   ;(attribute :state/removed
   ;  :db.type/ref
   ;  :db/isComponent true
   ;  :db/cardinality :db.cardinality/many)

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
     :db/cardinality :db.cardinality/many
     :db/isComponent true)

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

(defn mk-client-schema []
  [
    (attribute :explanation/binding-ids
       :db.type/ref
       :db/cardinality :db.cardinality/many)

    (attribute :explanation/request-id
       :db.type/ref
       :db/cardinality :db.cardinality/many)

    (attribute :explanation/match-ids
       :db.type/ref
       :db/cardinality :db.cardinality/many)

    (attribute :explanation/condition-ids
       :db.type/ref
       :db/cardinality :db.cardinality/many)

    (attribute :explanation/log-entry
      :db.type/hash-map
      :db/unique :db.unique/identity)

    (attribute :rule-history/event-meta
      :db.type/hash-map
      :db/cardinality :db.cardinality/many)

    (attribute :rule-history/event-ids
      :db.type/ref
      :db/cardinality :db.cardinality/many)

    (attribute :fact-tracker/viewer-ids
      :db.type/ref
      :db/cardinality :db.cardinality/many)

    (attribute :fact-tracker/occurrence-ids
      :db.type/ref
      :db/cardinality :db.cardinality/many)


    (attribute :fact-tracker.viewer/first-event-candidate-ids
      :db.type/ref
      :db/cardinality :db.cardinality/many)

    (attribute :fact-tracker/occurrence-map
      :db.type/hash-map
      :db/cardinality :db.cardinality/many)

    (attribute :fact-tracker/viewer-subs
      :db.type/vector
      :db/cardinality :db.cardinality/many)

    (attribute :schemas/one-to-many
      :db.type/keyword
      :db/cardinality :db.cardinality/many)])

(def db-schema (mk-db-schema))
(def client-schema (mk-client-schema))



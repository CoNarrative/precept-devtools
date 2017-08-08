(ns precept-devtools.transactions)

;; TODO.
;(defn create-binding-tx [])

(defn create-condition-tx
  [temp-id index type constraints fact-binding]
  (into {:db/id temp-id}
    #:condition{:type type
                :constraints (mapv str constraints)
                :fact-binding fact-binding})) ;; optional

(defn create-lhs-tx [temp-id condition-refs]
  (into {:db/id temp-id}
    #:lhs{:condition condition-refs}))

(defn create-rule-tx [temp-id name ns display-name rhs props]
  (into {:db/id temp-id}
    #:rule{:name name
           :ns (str ns)
           :display-name display-name
           :rhs (str rhs)
           :props (str props)}))

(defn create-fact-tx [temp-id fact]
  (into {:db/id temp-id}
    #:fact{:string (str fact)
           :e (str (:e fact))
           :a (str (:a fact))
           :v (str (:v fact))
           :t (:t fact)}))

(defn create-event-tx
  [temp-id type action event-number facts]
  (into {:db/id temp-id}
    #:event{:type type
            :action action
            :number event-number}))

(defn create-state-tx
  [temp-id state-id state-number]
  {:db/id temp-id
   :state/id (str state-id)
   :state/number state-number})

(ns precept-devtools.transactions)

;; TODO.
;(defn rm-quote-from-str [])

(defn create-match-tx [temp-id fact-ref]
  (into {:db/id temp-id}
    #:match{:fact fact-ref}))

(defn create-binding-tx [temp-id [variable value]]
  (into {:db/id temp-id}
    #:binding{:variable (str variable) ;; name? str?
              :value (str value)})) ;; str??

(defn create-condition-tx
  [temp-id index {:keys [type constraints fact-binding] :as condition}]
  (into {:db/id temp-id}
    #:condition{:type type
                :constraints (mapv #(str `'~%) constraints)
                :fact-binding fact-binding})) ;; optional

(defn create-lhs-tx [temp-id condition-refs]
  (into {:db/id temp-id}
    #:lhs{:conditions condition-refs}))

(defn create-rule-tx [temp-id name ns-name display-name rhs props]
  (into {:db/id temp-id}
    #:rule{:name name
           :ns (str ns-name)
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
  [temp-id type event-number]
  (into {:db/id temp-id}
    #:event{:type type
            :number event-number}))
            ;:action action})) ;;optional
            ;:match matches
            ;:binding bindings
            ;:fact facts}))
            ;:rule rule

(defn create-state-tx
  [temp-id state-id state-number]
  {:db/id temp-id
   :state/id state-id
   :state/number state-number})
   ;:state/events event-refs})


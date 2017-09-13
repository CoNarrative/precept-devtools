(ns precept-devtools.transactions
  (:require [precept.listeners :as l]))


(defn mk-id [] (java.util.UUID/randomUUID))

(defn update-tx-facts!
  [a eid-str-tuples]
  (apply (partial swap! a assoc) eid-str-tuples))

;; TODO.
;(defn rm-quote-from-str [])

(defn create-match-tx
  [temp-id fact-ref]
  (into {:db/id temp-id}
    #:match{:fact fact-ref}))

(defn create-binding-tx
  [temp-id [variable value]]
  (into {:db/id temp-id}
    #:binding{:variable (str variable) ;; name? str?
              :value (str value)})) ;; str??

(defn create-condition-tx
  [temp-id index {:keys [type constraints fact-binding] :as condition}]
  (into {:db/id temp-id}
    #:condition{:type type
                :constraints (mapv #(str `'~%) constraints)
                :fact-binding fact-binding})) ;; optional

(defn create-lhs-tx
  [temp-id condition-refs]
  (into {:db/id temp-id}
    #:lhs{:conditions condition-refs}))

(defn create-rule-tx
  [temp-id name ns-name display-name rhs props lhs-ref]
  (into {:db/id temp-id}
    #:rule{:name name
           :ns (str ns-name)
           :display-name display-name
           :lhs lhs-ref
           :rhs (str rhs)
           :props (str props)}))

(defn create-fact-tx
  [temp-id fact]
  (into {:db/id temp-id}
    #:fact{:string (str fact)
           :e (str (:e fact))
           :a (str (:a fact))
           :v (str (:v fact))
           :t (:t fact)}))

(defn render-diff [];added-strs removed-strs]
  (let [;added (map cljs.reader/read-string added-strs)
        ;removed (map cljs.reader/read-string removed-strs)
        added [{:e 1 :a :foo :v 2 :t 3} {:e 2 :a :foo :v "miss" :t 5}]
        removed [{:e 1 :a :foo :v 1 :t 4} {:e 2 :a :bar :v "miss2" :t 6}]
        ; Reduce into m with :added, :removed, :replaced [[added removed]] and :added
        ; :removed facts not in replaced
        e-a (juxt :e :a)
        replacements (for [added-fact added
                           removed-fact removed
                           :when (= (e-a added-fact) (e-a removed-fact))]
                        [added-fact removed-fact])
        rt-added (remove (set (flatten replacements)) added)
        rt-removed (remove (set (flatten replacements)) removed)]
    {:added rt-added :removed rt-removed :replaced replacements}))

;(render-diff)


(defn create-event-tx
  [event-id type event-number]
  (into {:db/id event-id}
    #:event{:type type
            :number event-number}))
            ;:fact facts
            ;;optional ---
            ;:action action
            ;:rule rule
            ;:match matches
            ;:binding bindings

(defn create-state-tx
  [temp-id state-id state-number]
  {:db/id temp-id
   :state/id state-id
   :state/number state-number})
   ;:state/events event-refs})

(defn event->facts
  [*tx-facts {:keys [id state-id state-number type action event-number bindings
                     matches name facts ns-name display-name lhs rhs props]}]
  (let [fact-txs (mapv #(create-fact-tx (mk-id) %) facts)
        _ (update-tx-facts! *tx-facts
            (mapcat #(vector (:fact/string %) (:db/id %))
              fact-txs))
        bindings-txs (mapv #(create-binding-tx (mk-id) %) bindings)
        conditions-txs (->> lhs
                         (map-indexed
                           (fn [index condition]
                             (create-condition-tx (mk-id) index condition)))
                         (into []))
        condition-refs (mapv :db/id conditions-txs)
        ;; write query to find fact by eavt where eventnum < this one
        ;; so we can use its ref here, unless we want to write a rule
        ;; that ensures uniqueness by eavt (or fact string)
        ;; any match must have been introduced as a fact previously, either
        ;; in a previous tx or this one
        ;; only handling case where came in on this state tx now, so need
        ;; to look back through previous events
        matches-as-facts (->> matches
                           (mapv first) ;;drop token id
                           (mapv str))
        fact-refs-for-matches (mapv #(get @*tx-facts %) matches-as-facts)
        matches-txs (mapv #(create-match-tx (mk-id) %) fact-refs-for-matches)
        lhs-tx (when (not action)
                 (vector
                   (create-lhs-tx (mk-id) condition-refs)))
        lhs-ref (:db/id (first lhs-tx))
        rule-tx (when (not action)
                  (vector
                    (create-rule-tx (mk-id)
                      name ns-name display-name `'~rhs props lhs-ref)))
        event-tx (create-event-tx id type event-number)]
    (conj
      (concat fact-txs rule-tx bindings-txs conditions-txs matches-txs lhs-tx)
      (into {}
        (remove
          (fn [[k v]]
            (cond
              (and (coll? v) (empty? v)) true
              (nil? v) true
              :default false))
          (assoc event-tx :event/matches (mapv :db/id matches-txs)
                          :event/bindings (mapv :db/id bindings-txs)
                          :event/facts (mapv :db/id fact-txs)
                          :event/rule (:db/id (first rule-tx))
                          :event/action action))))))

(defn events->diff
  [events]
  (-> events
    (l/split-ops)
    (l/diff-ops)))

(defn diff-with-str-facts
  [events]
  (reduce
    (fn [acc [k v]]
        (assoc acc k (mapv str v)))
    {}
    (events->diff events)))

(defn state->facts
  [events]
  (let [{:keys [state-id state-number]} (first events)
        state-tx (create-state-tx (mk-id) state-id state-number)
        *fact-str->eid (atom {})
        event-txs (mapcat #(event->facts *fact-str->eid %) events)
        state-diff (diff-with-str-facts events)]
    (conj event-txs
      (assoc state-tx :state/events
                      (mapv :db/id (filter :event/number event-txs))
                      :state/added (:added state-diff)
                      :state/removed (:removed state-diff)))))

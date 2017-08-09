(ns precept-devtools.routes.ws
    (:require [compojure.core :refer [defroutes GET POST]]
              [taoensso.sente :as sente]
              [mount.core :refer [defstate]]
              [cognitect.transit :as t]
              [precept-devtools.transactions :as txs]
              [taoensso.sente.packers.transit :as sente-transit]
              [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
              [precept.core :as core]
              [precept-devtools.rules :refer [devtools-session]])
    (:import [java.io ByteArrayInputStream]))

(defstate socket
  :start (sente/make-channel-socket!
           (get-sch-adapter)
           {:user-id-fn (fn [req] (java.util.UUID/randomUUID))}))
            ;:packer (sente-transit/get-transit-packer)}))

(def chsk-send! (:send-fn socket))
(def connected-uids (:connected-uids socket))

(defmulti handle-message :id)

(defmethod handle-message :chsk/uidport-open [x]
  ((:send-fn x) (:uid x) [:devtools/init {:hello true}]))

(defn mk-id [] (java.util.UUID/randomUUID))

(defn update-tx-facts!
  [a eid-str-tuples]
  (apply (partial swap! a assoc) eid-str-tuples))

(defn event->facts
  [*tx-facts {:keys [state-id state-number type action event-number bindings
                     matches name facts ns-name display-name lhs rhs props]}]
  (let [fact-txs (mapv #(txs/create-fact-tx (mk-id) %) facts)
        _ (update-tx-facts! *tx-facts
            (mapcat #(vector (:fact/string %) (:db/id %))
              fact-txs))
        rule-tx (when (not action)
                  (vector
                    (txs/create-rule-tx (mk-id)
                      name ns-name display-name `'~rhs props)))
        bindings-txs (mapv #(txs/create-binding-tx (mk-id) %) bindings)
        conditions-txs (->> lhs
                        (map-indexed
                          (fn [index condition]
                            (txs/create-condition-tx (mk-id) index condition)))
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
        matches-txs (mapv #(txs/create-match-tx (mk-id) %) fact-refs-for-matches)
        lhs-tx (when (not action)
                 (vector
                   (txs/create-lhs-tx (mk-id) condition-refs)))
        event-tx (txs/create-event-tx (mk-id) type event-number)]
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
                          :event/rule (mapv :db/id rule-tx)
                          :event/action action))))))

(defn state->facts
  [state]
  (let [{:keys [state-id state-number]} (first state)
        state-tx (txs/create-state-tx (mk-id) state-id state-number)
        *eid->fact-str (atom {})
        event-txs (mapcat #(event->facts *eid->fact-str %) state)]
    (conj event-txs
      (assoc state-tx :state/events
                      (mapv :db/id (filter :event/number event-txs))))))

(defmethod handle-message :devtools/update [m]
  (println "Received update: " (:uid m))
  (clojure.pprint/pprint (:?data m))
  (let [in (-> (:?data m) (.getBytes "UTF-8") (ByteArrayInputStream.))
        r (t/read (t/reader in :json-verbose))]
    (core/then (state->facts r))))

(defmethod handle-message :chsk/handshake [_])
(defmethod handle-message :chsk/uidport-close [_])
(defmethod handle-message :chsk/ws-ping [_])
(defmethod handle-message :chsk/bad-event [m]
  (println "Bad event: ")
  (clojure.pprint/pprint m))
(defmethod handle-message :default [x] (println "unhandled" x))

(defstate router
  :start (sente/start-chsk-router! (:ch-recv socket) handle-message)
  :stop (router))

(defroutes ws-routes
  (GET "/chsk" req ((:ajax-get-or-ws-handshake-fn socket) req))
  (POST "/chsk" req ((:ajax-post-fn socket) req)))

(defstate devtools-session-state
  :start (core/start! {:session devtools-session
                       :facts [[:transient :start true]]}))
(ns precept-devtools.routes.ws
    (:require [compojure.core :refer [defroutes GET POST]]
              [taoensso.sente :as sente]
              [mount.core :refer [defstate]]
              [cognitect.transit :as t]
              [precept-devtools.transactions :as txs]
              [taoensso.sente.packers.transit :as sente-transit]
              [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]])
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

(def res (atom {:data nil}))

@res

(defn mk-id []
 (java.util.UUID/randomUUID))

(def eid->fact-str (atom {}))

(defn update-tx-facts!
  [a eid-str-tuples]
  (apply (partial swap! a assoc) eid-str-tuples))

;(defn event->facts [m])
(let [entry (second (:data @res))
      {:keys [state-id state-number type action event-number
              bindings name facts ns-name display-name rhs props]} entry
      ;; TODO.
      ;; Won't need to create state fact for every event. Can be created
      ;; by the caller and event-refs assoc'd
      state-tx (txs/create-state-tx (mk-id) state-id state-number)
      ;; TODO. matches, bindings, facts, rule
      event-tx (txs/create-event-tx (mk-id) type action event-number)
      fact-txes (mapv #(txs/create-fact-tx (mk-id) %) facts)
      _ (update-tx-facts! eid->fact-str
          (mapcat #(vector (:fact/string %) (:db/id %))
            fact-txes))
      rule-tx (when (not action)
                (txs/create-rule-tx (mk-id)
                  name ns-name display-name `'~rhs props))
      bindings-txs (mapv #(txs/create-binding-tx (mk-id) %) bindings)
      condition-txs (->> (:lhs entry)
                      (map-indexed
                        (fn [index condition]
                          (txs/create-condition-tx (mk-id) index condition)))
                      (into []))
      condition-refs (mapv :db/id condition-txs)
      ;; write query to find fact by eavt where eventnum < this one
      ;; so we can use its ref here, unless we want to write a rule
      ;; that ensures uniqueness by eavt (or fact string)
      ;; any match must have been introduced as a fact previously, either
      ;; in a previous tx or this one
      ;; only handling case where came in on this state tx now, so need
      ;; to look back through previous events
      matches-as-facts (->> (:matches entry)
                          (mapv first) ;;drop token id
                          (mapv str))
      fact-refs-for-matches (mapv #(get @eid->fact-str %) matches-as-facts)
      matches-txs (mapv #(txs/create-match-tx (mk-id) %) fact-refs-for-matches)
      lhs-tx (when (not action)
               (txs/create-lhs-tx (mk-id) condition-refs))]
  (println "State\n" state-tx)
  (println "Event\n" event-tx)
  (println "Facts\n" fact-txes)
  (println "LHS\n" lhs-tx)
  (println "Rule\n" rule-tx)
  (println "Condition\n" condition-txs)
  (println "Matches\n" matches-txs)
  (println "Bindings\n" bindings-txs))

(defmethod handle-message :devtools/update [m]
  (println "Received update: " (:uid m))
  (clojure.pprint/pprint (:?data m))
  (let [in (-> (:?data m) (.getBytes "UTF-8") (ByteArrayInputStream.))
        r (t/read (t/reader in :json-verbose))]
    (swap! res assoc :data r)))

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

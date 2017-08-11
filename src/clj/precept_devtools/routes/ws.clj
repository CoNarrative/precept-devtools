(ns precept-devtools.routes.ws
    (:require [compojure.core :refer [defroutes GET POST]]
              [taoensso.sente :as sente]
              [mount.core :refer [defstate]]
              [cognitect.transit :as t]
              [precept-devtools.transactions :as txs]
              [taoensso.sente.packers.transit :as sente-transit]
              [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
              [precept.core :as core]
              [precept-devtools.db :as db]
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
  (println "[devtools-server] A client connected" (:uid x)))

(defmethod handle-message :devtools/update [m]
  (println "Received update: " (:uid m))
  (clojure.pprint/pprint (:?data m))
  (let [in (-> (:?data m) (.getBytes "UTF-8") (ByteArrayInputStream.))
        events (t/read (t/reader in :json-verbose))
        facts (txs/state->facts events)]
    (swap! db/db update :log conj events)
    (swap! db/db update :states (fn [xs] (into [] (conj xs facts))))
    (core/then facts)))

(defmethod handle-message :states/dump [m]
  ((:?reply-fn m) {:states (:states @db/db)}))

(defmethod handle-message :log/dump [m]
  ((:?reply-fn m) {:log (:log @db/db)}))

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
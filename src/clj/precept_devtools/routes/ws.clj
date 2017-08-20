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

(defn decode-payload [req]
  (let [encoding (get-in req [:?data :encoding])
        payload (get-in req [:?data :payload])
        in (-> payload (.getBytes "UTF-8") (ByteArrayInputStream.))]
    (t/read (t/reader in encoding))))

(defn visualizer-client? [x]
  (get-in x [:ring-req :params :visualizer?]))

(defmulti handle-message :id)

(defmethod handle-message :chsk/uidport-open [x]
  (println "[devtools-server] A client connected" (:uid x))
  (if (visualizer-client? x)
    (do
      (swap! db/db update :visualizer-uids (fn [y] (into #{} (conj y (:uid x)))))
      ((:send-fn x) (:uid x) [:state/update (flatten (:states @db/db))]))
    (reset! db/db {})) ; zero the db for dev
  (println "[devtools-server] Visualizers" (get-in @db/db [:visualizer-uids])))

(defmethod handle-message :devtools/update [m]
  (println "Received update from app: " (:uid m))
  (let [events (decode-payload m)
        facts (txs/state->facts events)]
    ;; TODO. Concat? flat list but no get state by index n
    (swap! db/db update :log conj events)
    (swap! db/db update :states (fn [xs] (into [] (conj xs facts))))
    (core/then facts)
    (doseq [x (:visualizer-uids @db/db)]
      ((:send-fn m) x [:state/update facts]))))

(defmethod handle-message :devtools/schemas [m]
  (println "Received schemas from app: " (:uid m))
  (let [schemas (decode-payload m)
        combined-schemas (remove nil? (concat (:db schemas) (:client schemas)))]
    (swap! db/db assoc :schemas combined-schemas)))

(defmethod handle-message :states/dump [m]
  ((:?reply-fn m) {:payload (:states @db/db)}))

(defmethod handle-message :log/dump [m]
  ((:?reply-fn m) {:payload (:log @db/db)}))

(defmethod handle-message :schemas/get [m]
  ((:?reply-fn m) {:payload (:schemas @db/db)}))

(defmethod handle-message :chsk/uidport-close [x]
  (println "[devtools-server] A client disconnected" (:uid x))
  (when (visualizer-client? x)
    (swap! db/db update :visualizer-uids
      (fn [y] (into #{} (disj y (:uid x))))))
  (println "[devtools-server] Visualizers" (get-in @db/db [:visualizer-uids])))

(defmethod handle-message :chsk/handshake [_])
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
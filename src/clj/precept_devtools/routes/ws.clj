(ns precept-devtools.routes.ws
    (:require [compojure.core :refer [defroutes GET POST]]
              [taoensso.sente :as sente]
              [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]
              [cognitect.transit :as t]
              [mount.core :refer [defstate]]
              [precept.core :as core]
              [precept.orm :as orm]
              [precept.schema :as schema]
              [precept-devtools.db :as db]
              [precept-devtools.rules :refer [devtools-session]]
              [precept-devtools.transactions :as txs]
              [precept.util :as util])
    (:import [java.io ByteArrayInputStream]))

(defn log [args])
  ;(apply println args))

(defstate socket
  :start (sente/make-channel-socket!
           (get-sch-adapter)
           {:user-id-fn (fn [req] (java.util.UUID/randomUUID))}))
            ;:packer (sente-transit/get-transit-packer)}))

(def chsk-send! (:send-fn socket))
(def connected-uids (:connected-uids socket))

(defn decode-payload
  "Assumes transit encoding and req map with keys :encoding, :payload"
  [req]
  (let [encoding (get-in req [:?data :encoding])
        payload (get-in req [:?data :payload])
        in (-> payload (.getBytes "UTF-8") (ByteArrayInputStream.))]
    (t/read (t/reader in encoding))))

(defn visualizer-client? [x]
  (get-in x [:ring-req :params :visualizer?]))

(defmulti handle-message :id)

(defmethod handle-message :chsk/uidport-open [x]
  (if (visualizer-client? x)
    (do
      (println "[devtools-server] Visualizer connected" (:uid x))
      (let [cache @db/db
            payload {:facts (flatten (:states cache))
                     :orm-states (:orm-states cache)}]
        (swap! db/db update :visualizer-uids (fn [y] (into #{} (conj y (:uid x)))))
        (println "[devtools-server] Sending payload to visualizer: ")
        ;(clojure.pprint/pprint payload)
        ((:send-fn x) (:uid x) [:visualizer/init payload])))
    (do (println "[devtools-server] Precept app connected " (:uid x))
        (reset! db/db (dissoc db/initial-state :visualizer-uids))))) ; zero the db for dev
  ;(println "[devtools-server] Visualizers" (get-in @db/db [:visualizer-uids])))

(defn update-orm-state! [facts]
  (let [ops (select-keys
              (first (filter #(every? % #{:state/added :state/removed}) facts))
              [:state/added :state/removed])
        added (->> ops :state/added (mapv (comp util/record->vec read-string)))
        removed (->> ops :state/removed (mapv (comp util/record->vec read-string)))
        *last-orm-state (atom (or (last (:orm-states @db/db)) {}))
        *next-orm-state (-> *last-orm-state
                          (orm/update-tree!
                            (:ancestors-fn @db/db)
                            {:remove removed})
                          (orm/update-tree!
                            (:ancestors-fn @db/db)
                            {:add added}))]
    (swap! db/db update :orm-states #(into [] (conj % @*next-orm-state)))
    (last (:orm-states @db/db))))

(defmethod handle-message :devtools/update [m]
  (println "Received update from app: " (:uid m))
  (let [events (decode-payload m)
        ;_ (println "First event:" (keys events))
        facts (txs/state->facts events)
        orm-state (update-orm-state! facts)
        payload {:orm-state orm-state :facts facts}]
    ;; TODO. Concat? flat list but no get state by index n
    (swap! db/db update :log #(into [] (conj % (txs/merge-schema-actions events))))
    (swap! db/db update :states (fn [xs] (into [] (conj xs facts))))
    (core/then facts)
    (doseq [x (:visualizer-uids @db/db)]
      (println "Notifying visualizer client ... " x)
      (clojure.pprint/pprint payload)
      ((:send-fn m) x [:state/update payload]))))

(defmethod handle-message :devtools/schemas [m]
  (println "Received schemas from app: " (:uid m))
  (let [schemas (decode-payload m)
        combined-schemas (remove nil? (concat (:db schemas) (:client schemas)))
        ancestors-fn (-> combined-schemas (schema/schema->hierarchy) (util/make-ancestors-fn))]
    (swap! db/db assoc :schemas combined-schemas)
    (swap! db/db assoc :ancestors-fn ancestors-fn)))

(defmethod handle-message :states/dump [m]
  ((:?reply-fn m) {:payload (:states @db/db)}))

(defmethod handle-message :log/dump [m]
  ((:?reply-fn m) {:payload (:log @db/db)}))

(defmethod handle-message :schemas/get [m]
  ((:?reply-fn m) {:payload (:schemas @db/db)}))

(defmethod handle-message :log/entry-by-coords [m]
  (let [coords (-> m :event (second))]
    ((:?reply-fn m) {:payload (get-in (:log @db/db) coords)})))

(defmethod handle-message :chsk/uidport-close [x]
  (println "[devtools-server] A client disconnected" (:uid x))
  (when (visualizer-client? x)
    (swap! db/db update :visualizer-uids
      (fn [y] (into #{} (disj y (:uid x))))))
  (println "[devtools-server] Visualizers" (get-in @db/db [:visualizer-uids])))

(defmethod handle-message :chsk/handshake [_])
(defmethod handle-message :chsk/ws-ping [_])
(defmethod handle-message :chsk/bad-event [m]
  (println "Bad event: "))
  ;(clojure.pprint/pprint m))
(defmethod handle-message :default [x] (println "unhandled"))

(defstate router
  :start (sente/start-chsk-router! (:ch-recv socket) handle-message)
  :stop (router))

(defroutes ws-routes
  (GET "/chsk" req ((:ajax-get-or-ws-handshake-fn socket) req))
  (POST "/chsk" req ((:ajax-post-fn socket) req)))

(defstate devtools-session-state
  :start (core/start! {:session devtools-session
                       :facts [[:transient :start true]]}))

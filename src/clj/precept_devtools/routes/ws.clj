(ns precept-devtools.routes.ws
    (:require [compojure.core :refer [defroutes GET POST]]
              [taoensso.sente :as sente]
              [mount.core :refer [defstate]]
              [taoensso.sente.server-adapters.http-kit :refer [get-sch-adapter]]))

(defstate socket
  :start (sente/make-channel-socket!
           (get-sch-adapter)
           {:user-id-fn (fn [req] (java.util.UUID/randomUUID))}))

(def chsk-send! (:send-fn socket))
(def connected-uids (:connected-uids socket))

(defmulti handle-message :id)

(defmethod handle-message :chsk/uidport-open [x]
  ((:send-fn x) (:uid x) [:devtools/init {:hello true}]))

(defmethod handle-message :devtools/update [m]
  (println "Received update: " (:uid m))
  ;(persist-state! (:?data m))
  (clojure.pprint/pprint (:?data m)))

(defmethod handle-message :chsk/handshake [_])
(defmethod handle-message :chsk/uidport-close [_])
(defmethod handle-message :chsk/ws-ping [_])
(defmethod handle-message :chsk/bad-event [m]
  (println "Bad event: ")
  (clojure.pprint/pprint m))

(defstate router
  :start (sente/start-chsk-router! (:ch-recv socket) handle-message)
  :stop (router))

(defroutes ws-routes
  (GET "/chsk" req ((:ajax-get-or-ws-handshake-fn socket) req))
  (POST "/chsk" req ((:ajax-post-fn socket) req)))

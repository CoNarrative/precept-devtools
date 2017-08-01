(ns precept-devtools.ws
  (:require [taoensso.sente :as sente]
            [mount.core :refer [defstate]]
            [precept-devtools.graph :as graph]))

(defstate socket
  :start (sente/make-channel-socket! "/chsk" {:type :auto}))

(defmulti handle-message first)
(defmethod handle-message :chsk/ws-ping [_])

(defmethod handle-message :init
  [[msg-name msg]]
  (graph/init! msg))

(defmulti handle-event :id)
(defmethod handle-event :chsk/state [_])
(defmethod handle-event :chsk/handshake [_])

(defmethod handle-event :chsk/recv [{:keys [?data]}]
  (handle-message ?data))

(defstate router
  :start (sente/start-chsk-router! (:ch-recv @socket) handle-event))

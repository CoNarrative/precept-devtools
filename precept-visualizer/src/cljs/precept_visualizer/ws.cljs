(ns precept-visualizer.ws
  (:require [taoensso.sente :as sente]
            [mount.core :refer [defstate]]
            [precept-visualizer.state :as state]
            [precept.core :as core]))

(declare socket)

(defn connect! [host path]
  (let [{:keys [chsk ch-recv send-fn state] :as socket}
        (sente/make-channel-socket!
          path
          {:host host
           :params {:visualizer? true}
           :type :auto})]
    (def chsk       chsk)
    (def ch-chsk    ch-recv)
    (def chsk-send! send-fn)
    (def chsk-state state)
    socket))

(defn get-log []
  ((:send-fn @socket) [:log/dump]
    5000
    (fn [reply]
      (if (sente/cb-success? reply)
        (println "[visualizer] :log/dump" reply)
        (println "Error" reply)))))

(defn get-states []
  ((:send-fn @socket) [:states/dump]
    5000
    (fn [reply]
      (if (sente/cb-success? reply)
        (println "[visualizer] :states/dump" reply)
        (println "Error" reply)))))

(defn get-schemas []
  ((:send-fn @socket) [:schemas/get]
    5000
    (fn [reply]
      (if (sente/cb-success? reply)
        (println "[visualizer] :schemas/get" reply)
        (println "Error" reply)))))

(defmulti handle-message first)
(defmethod handle-message :chsk/ws-ping [_])

(defmethod handle-message :visualizer/init [[_ payload]]
  (println "Rec'd data from server")
  (reset! state/orm-ratom (:orm-states payload))
  (core/then (:facts payload)))

(defmethod handle-message :state/update [[_ payload]]
  (println "Rec'd update from server")
  (swap! state/orm-ratom conj (:orm-state payload))
  (core/then (:facts payload)))

(defmulti handle-event :id)

(defmethod handle-event :chsk/state [{:keys [?data]}]
  (let [[last-state this-state] ?data]
    (when (:first-open? this-state)
      (do (println (str "[visualiser] Connected to devtools server "))))))

(defmethod handle-event :chsk/handshake [_])

(defmethod handle-event :chsk/recv [{:keys [?data]}]
  (handle-message ?data))

(defstate socket
  :start (connect! core/default-devtools-host "/chsk"))

(defstate router
  :start (sente/start-chsk-router! (:ch-recv @socket) handle-event))

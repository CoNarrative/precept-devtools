(ns precept-visualizer.ws
  (:require [taoensso.sente :as sente]
            [mount.core :refer [defstate]]
            [precept-visualizer.state :as state]
            [precept.core :as precept]))

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

(defn get-log-id [id]
  ((:send-fn @socket) [:log/id id]
    5000
    (fn [reply]
      (if (sente/cb-success? reply)
        (println "[visualizer] :log/dump" reply)
        (println "Error" reply)))))

(defn get-log-entry-by-coords [[state-number event-number]]
  ((:send-fn @socket) [:log/entry-by-coords [state-number event-number]]
    5000
    (fn [reply]
      (if (sente/cb-success? reply)
        (do
          (println "[visualizer] :log/entry-by-coords")
          (cljs.pprint/pprint (:payload reply))
          (precept/then {:db/id (-> reply :payload :id)
                         :event/log-entry (:payload reply)}))
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

(defmethod handle-message any? [x] (println "Something" x))
(defmethod handle-message :chsk/ws-ping [_])

(defmethod handle-message :visualizer/init [[_ payload]]
  (println ":visualizer/init")
  (reset! state/orm-ratom (:orm-states payload))
  (reset! state/rule-definitions (:rule-definitions payload))
  (precept/then (:facts payload))
  (precept/then (:schemas payload)))

(defmethod handle-message :state/update [[_ payload]]
  (println ":state/update")
  (cljs.pprint/pprint payload)
  (swap! state/orm-ratom conj (:orm-state payload))
  (precept/then (:facts payload)))

(defmulti handle-event :id)
(defmethod handle-event :default ; Fallback
  [{:as ev-msg :keys [event]}]
  (js/console.log "Unhandled event: %s" (pr-str event)))

(defmethod handle-event :chsk/bad-event [x]
  (println "[visualizer] got bad event" x))

(defmethod handle-event :chsk/bad-package [x]
  (println "[visualizer] got bad package" x))

(defmethod handle-event :chsk/state [{:keys [?data]}]
  (let [[last-state this-state] ?data]
    (when (:first-open? this-state)
      (do (println (str "[visualiser] Connected to devtools server "))))))

(defmethod handle-event :chsk/handshake [_])

(defmethod handle-event :chsk/recv [{:keys [?data]}]
  (handle-message ?data))

(defstate socket
  :start (connect! (:host precept/default-devtools-options) "/chsk"))

(defstate router
  :start (sente/start-chsk-router! (:ch-recv @socket) handle-event))

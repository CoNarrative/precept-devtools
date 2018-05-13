(ns precept-visualizer.ajax
  (:require [precept-visualizer.config :as config]
            [precept-visualizer.state :as state]
            [precept.core :as precept]))

(defn get-log-entries-for-state-range [[n1 n2]]
  (-> (js/fetch (str config/api-url "/log/range/" n1 "/" n2))
      (.then (fn [x] (.text x)))
      (.then cljs.reader/read-string)
      (.catch js/console.error)))

(defn update-event-log! [[n1 n2]]
  (-> (get-log-entries-for-state-range [n1 n2])
      (.then #(let [flattened (reduce concat %)
                    state-numbers (map :state-numbers flattened)]
                (do
                  (precept/then [:transient :update-state-numbers (set state-numbers)])
                  (swap! state/event-log concat flattened))))))
(comment
  (-> (get-log-entries-for-state-range [0 3])
      (.then #(->> %
                   (reduce concat)
                   (swap! state/event-log concat)))
      (.catch pr)))

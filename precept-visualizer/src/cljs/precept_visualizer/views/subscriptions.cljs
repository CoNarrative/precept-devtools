(ns precept-visualizer.views.subscriptions
  (:require [reagent.core :as r]
            [precept.core :as precept]
            [precept-visualizer.event-parser :as event-parser]
            [precept.spec.sub :as sub]))


(defn facts-with-attr-at-state-num [states attr n]
  (let [state (if (number? n)
                (nth states n {})
                {})]
    (->> state
      (filter (fn [[e av-map]] (attr av-map)))
      (into {}))))

(defn sub-responses-for-state-num
  "Returns keyed by sub id"
  [states n]
  (->> (facts-with-attr-at-state-num states ::sub/response n)
    (mapv (fn [[id av]] {id (::sub/response av)}))
    (reduce merge)))

(defn sub-requests-for-state-num
  "Returns keyed by sub id"
  [states n]
  (->> (facts-with-attr-at-state-num states ::sub/request n)
    (mapv (fn [[id av]] {id (::sub/request av)}))
    (reduce merge)))

(defn subscriptions-list [*orm-states]
  (let [state-index (:state/number @(precept/subscribe [:state-tree]))
        states @*orm-states
        sub-requests (sub-requests-for-state-num states state-index)
        cur-sub-responses (sub-responses-for-state-num states state-index)
        prev-sub-responses (sub-responses-for-state-num states (dec state-index))]
    [:div
     (for [[id sub-name] sub-requests]
       (let [cur-v (get cur-sub-responses id)
             prev-v (get prev-sub-responses id)
             from-event-data (if (coll? cur-v)
                                (event-parser/prettify-all-facts cur-v {:trim-uuids? true})
                                cur-v)]
         [:div {:key id}
          [:h4 (str sub-name)]
          [:div "Current:"
            [:pre (str from-event-data)]]
          [:div "Previous:"
           [:pre (str (event-parser/prettify-all-facts prev-v {:trim-uuids? true}))]]]))]))





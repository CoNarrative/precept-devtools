(ns precept-visualizer.views.state-tree
  (:require [precept.core :as precept]
            [reagent.core :as r]
            [net.cgrand.packed-printer :as packed]
            [precept-visualizer.util :as util]
            [precept-visualizer.event-parser :as event-parser]
            [precept-visualizer.matching :as matching]))


(defn elided-e [x]
  (if (keyword? x)
    [:span (str x)]
    (let [elided? (r/atom true)]
      (fn []
       (if @elided?
         [:span {:style {:cursor "pointer"}
                 :on-click #(swap! elided? not)}
           (str (subs (str x) 0 8)
            "...")]
         [:span {:style {:cursor "pointer"}
                 :on-click #(swap! elided? not)}
           (str x)])))))


(defn row [[e a v] styles]
  [:tr {:key (str e "-" a "-" (hash v))}
   [:td {:style {:paddingLeft 12}}
    (when (not= "" e)
      [:pre {:style (merge {:padding 0} styles)}
       [elided-e e]])]
   [:td
    [:pre {:style (merge {:padding 0} styles)}
      (str a)]]
   [:td {:on-click #(println "collapse/expand")}
    [:pre {:style (merge
                    {:padding 0}
                    styles)}
     (let [from-event-data (if (coll? v)
                             (event-parser/prettify-all-facts v {:trim-uuids? true})
                             v)]
       (matching/format-edn-str from-event-data))]]])

(defn entity-rows [i [e av]]
  (let [styles {:background-color (if (even? i) "#888" "#fff")
                :color (if (even? i) "#fff" "#000")}]
    [:tbody {:style styles}
     (concat
       (list ^{:key (str e av i)}
         [row [e (ffirst av) (second (first av))] styles])
       (map
         (fn [[a v]]
           ^{:key (str i a v)} [row ["" a v] styles])
         (rest av)))]))

;(defn state-tree [*orm-states])
;; What to name???
(defn main [*orm-states theme]
  (let [sub (precept/subscribe [:state-tree])
        collapsed? (r/atom false)]
    (fn [*orm-states]
      (let [tree (get @*orm-states (:state/number @sub))]
        [:div {:style {:margin-left 24}}
         [:h4 {:on-click #(reset! collapsed? (not @collapsed?))}
          "State tree ^"]
         (when (not @collapsed?)
           [:table
            [:thead
             [:tr
              [:th
               {:style {:cursor "-webkit-grab"
                        :paddingLeft 12}}
               "e"]
              [:th
               {:style {:cursor "-webkit-grab"
                        :width "30%"}
                :on-mouse-down #(precept/then
                                   [[:state-tree :state-tree.col/mouse-down-evt (doto % (.persist))]
                                    [:state-tree :state-tree.col/mouse-down-on-col :a]])
                :on-mouse-up #(precept/then
                                  {:db/id :transient
                                   :state-tree.col/mouse-up-on-col
                                   :a})
                :on-mouse-move #(precept/then
                                 [[:transient :state-tree.col/mouse-move-evt (doto % (.persist))]
                                  [:transient :state-tree.col/mouse-move-on-col :a]])}
               "a"]
              [:th
               {:style {:cursor "-webkit-grab"}}
               "v"]]]
            (map-indexed
              (fn [i [e av]]
                ^{:key (str e)} [entity-rows i [e av]])
              tree)])]))))

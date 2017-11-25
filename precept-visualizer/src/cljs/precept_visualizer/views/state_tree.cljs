(ns precept-visualizer.views.state-tree
  (:require [precept.core :as precept]
            [reagent.core :as r]))


(defn elided-e [x]
  (if (keyword? x)
    [:span (str x)]
    (let [elided? (r/atom true)]
      (fn []
       (if @elided?
         [:span {:on-click #(swap! elided? not)}
           (str (subs(str x) 0 6)
            "...")]
         [:span {:on-click #(swap! elided? not)}
           (str x)])))))


(defn row [[e a v]]
  [:tr {:key (str e "-" a "-" (hash v))}
   [:td [elided-e e]]
   [:td (str a)]
   [:td {:on-click #(println "collapse/expand")}
    [:div (with-out-str (cljs.pprint/pprint v))]]])


;(defn state-tree [*orm-states])
;; What to name???
(defn main [*orm-states]
  (let [sub @(precept/subscribe [:state-tree])
        tree (get @*orm-states (:state/number sub))
        collapsed? (r/atom false)
        _ (println (count @*orm-states))
        _ (println (:state/number sub))]
    (fn [*orm-states]
      [:div {:style {:margin-left 24}}
       [:h4 {:on-click #(do (println "click")
                            (reset! collapsed? (not @collapsed?)))}
        "State tree ^"]
       (when (not @collapsed?)
         [:table
          [:thead
           [:tr
            [:th "e"]
            [:th {:style {:width "30%"}}
             "a"]
            [:th "v"]]]
          (map-indexed
            (fn [i [e av]]
              [:tbody
                 {:key (str e i)
                  :style {:background-color (if (even? i) "#888" "#fff")
                          :color (if (even? i) "#fff" "#000")}}
                (concat
                  (list ^{:key (str e av i)} [row [e (ffirst av) (second (first av))]])
                  (map
                    (fn [[a v]]
                      [:tr {:key (str a v i)}
                        [:td
                         ""]
                        [:td (str a)]
                        [:td {:on-click #(println "collapse/expand")}
                          [:div (with-out-str (cljs.pprint/pprint v))]]])
                    (rest av)))])
           tree)])])))

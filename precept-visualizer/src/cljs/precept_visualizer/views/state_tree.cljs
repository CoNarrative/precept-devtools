(ns precept-visualizer.views.state-tree
  (:require [precept.core :as precept]))

;(defn state-tree [*orm-states])
;; What to name???
(defn main [*orm-states]
  (let [sub @(precept/subscribe [:state-tree])
        tree (get @*orm-states (:state/number sub))
        _ (println (count @*orm-states))
        _ (println (:state/number sub))
        _ (println "state-tree render" tree)]
    [:div
     [:h4 "State tree"]
     [:div {:style {:display "flex" :justify-content "space-between"}}
      [:div "e"]
      [:div "a"]
      [:div "v"]]
     (map (fn [[e av]]
            ^{:key (str e)}
            [:div {:style {:display "flex"}}
             [:div {:style {:margin-right 15}}
              (str e)]
             [:div {:style {:display "flex" :flex 1 :flex-direction "column"}}
              (map
                (fn [[a v]]
                  ^{:key (str e "-" a "-" (hash v))}
                  [:div {:style {:min-width "100%" :display "flex" :justify-content "space-between"}}
                   [:div {:style {:flex 1}}
                    (str a)]
                   [:div {:style {:flex 1}}
                    (with-out-str (cljs.pprint/pprint v))]])
                av)]])
       tree)]))


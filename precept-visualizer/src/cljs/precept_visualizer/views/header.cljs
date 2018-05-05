(ns precept-visualizer.views.header
  (:require [precept.core :as precept]
            [precept-visualizer.views.topbar :as topbar]))


(defn header []
  (let [settings (precept/subscribe [:settings])
        theme (precept/subscribe [:selected-theme])]
    (fn []
      [:div
       [:div {:style {:display "flex"}}
        [:div {:style {:padding-left 18
                       :padding-right 12}}
         [:img {:style {:width 18 :height 18 :vertical-align "middle" :margin-right 12}
                :src "svg/precept-icon.svg"}]
         [:strong {:style {:vertical-align "middle"}}
          "Precept Devtools"]]
        [topbar/menu-header topbar/menu-data settings theme]]])))

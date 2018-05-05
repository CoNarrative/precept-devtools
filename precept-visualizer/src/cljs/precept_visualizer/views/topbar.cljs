(ns precept-visualizer.views.topbar
  (:require [precept-visualizer.views.consequents :as conseq]
            [precept-visualizer.themes :as themes]
            [reagent.core :as r]))

(def selected-icon " $")
(def right-arrow-icon " >")

(def menu-data
  [{:label "View"
    :children [{:label "State controls"
                :selected? :state-controls-visible?
                :on-click #(conseq/state-controls-visible (not (:state-controls-visible? @%)))}]}
   {:label "Themes"
    :children [{:label "Dark"
                :selected? (fn [state] (-> state :selected-theme (= ::themes/dark)))
                :on-click #(conseq/theme-is ::themes/dark)}
               {:label "Light"
                :selected? (fn [state] (-> state :selected-theme (= ::themes/light)))
                :on-click #(conseq/theme-is ::themes/light)}]}
   {:label "Settings"
    :children [{:label "Fact format"
                :icon right-arrow-icon
                :children [{:label "Vector"
                            :selected? (fn [state] (-> state :fact-format (= :vector)))
                            :on-click #(conseq/fact-format-is :vector)}
                           {:label "Map"
                            :selected? (fn [state] (-> state :fact-format (= :map)))
                            :on-click #(conseq/fact-format-is :map)}]}]}])


(defn menu-child-menu-item [local-db i item]
  (let [selected?-predicate (:selected? item)
        on-click (:on-click item)]
    [:div {:style {:display "flex"
                   :cursor "default"}
           :on-click #(-> local-db (on-click))}
     (:label item)
     (when (selected?-predicate @local-db)
       selected-icon)]))


(defn menu-child-menu [local-db i items]
  [:div {:style {:position "absolute"
                 :background (:background-color (:theme @local-db))
                 :padding "0px 20px"
                 :width 200}}
   (map-indexed
     (fn [i item]
       ^{:key i} [menu-child-menu-item local-db i item])
     items)])


(defn on-menu-item-mouse-enter [state path props]
  (swap! state assoc :hovered-item path)
  (when (:children props)
    (swap! state assoc :open-menus path)))

(defn on-menu-item-mouse-leave [state]
  (swap! state dissoc :hovered-item))

(defn menu-item [local-db path props]
  (let [open-menus (:open-menus @local-db)
        on-click (or (:on-click props) identity)
        selected?-fn (or (:selected? props) (constantly nil))]
    [:div
     {:style {:display "flex"
              :background (if (= path (:hovered-item @local-db))
                            "rgba(255,255,255,0.2)"
                            "transparent")}
      :on-mouse-enter #(on-menu-item-mouse-enter local-db path props)}
     [:div {:style {:width 200 :cursor "default"}
            :on-click #(-> local-db (on-click))}
      (:label props)
      (when-let [icon (:icon props)]
        icon)
      (when-let [selected? (selected?-fn @local-db)]
        selected-icon)]
     (when (and (:children props) (= path open-menus))
       [:div {:style {:position "relative" :right 0}}
        [menu-child-menu
         local-db
         path
         (:children props)]])]))


(defn on-header-item-click [state i]
  (when (empty? (:open-menus @state))
    (swap! state assoc :open-menus [i])))

(defn on-header-item-mouse-enter [state i]
  (when (not (empty? (:open-menus @state)))
    (swap! state assoc :open-menus [i])))

(defn on-header-item-mouse-leave [state i]
  (when (not (empty? (:open-menus @state)))
    (swap! state update :open-menus (fn [v] (into [] (remove #{i} v))))))

(defn menu-header-item [local-db i label children]
  (let [open? (= i (first (:open-menus @local-db)))]
    [:div
     {:style {:background (if open? "blue" "transparent")
              :padding-right 12
              :padding-left 12}
      :on-click #(on-header-item-click local-db i)
      :on-mouse-enter #(on-header-item-mouse-enter local-db i)}
     label
     (when open?
       [:div {:style {:position "absolute"
                      :width 200
                      :background (:background-color (:theme @local-db))
                      :display "flex"
                      :flex-direction "column"}}
        (map-indexed
          (fn [i2 props]
            ^{:key i2} [menu-item local-db [i [i2]] props])
          children)])]))


(defn menu-header [menu-data settings theme]
  (let [local-db (r/atom (merge @settings @theme {:open-menus []}))]
    [:div {:style {:display "flex"
                   :height 24
                   :vertical-align "middle"
                   :opacity 0.82
                   :background (:background-color (:theme @theme))}}
     (map-indexed
       (fn [i {:keys [label children]}]
         ^{:key i} [menu-header-item local-db i label children])
       menu-data)]))

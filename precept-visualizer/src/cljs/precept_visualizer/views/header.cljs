(ns precept-visualizer.views.header
  (:require [precept.core :as precept]
            [precept-visualizer.views.consequents :as conseq]
            [precept-visualizer.themes :as themes]
            [reagent.core :as r]))


(defn state-controls []
  (let [{:keys [tracking/state-number tracking/sync?
                max-state-number]} @(precept/subscribe [:header])]
    [:div {:style {:display "flex"
                       :flex-direction "column"
                       :background-color "grey"}}
         [:div {:style {:display "flex" :align-items "center"}}
          [:button
           {:class "button"
            :on-click #(conseq/tracking-state-number (dec state-number))
            :disabled (= state-number 0)}
           [:div {:class "large monospace"}
            "-"]]
          [:h3
           {:class "end"
            :style {:margin "0px 15px"}}
           (str "State " state-number)]
          [:button
           {:class "button"
            :on-click #(conseq/tracking-state-number (inc state-number))
            :disabled (= max-state-number state-number)}
           [:div {:class "large monospace"}
            "+"]]]
         [:div
          [:div {:class "form-item"}
           [:label {:class "checkbox"}
            [:input {:type "checkbox"
                     :checked sync?
                     :on-change #(conseq/tracking-state-synced? (not sync?))}]
            "Sync"]]]
         [:input {:type "range"
                  :style {:padding 0}
                  :min 0
                  :max max-state-number
                  :value state-number
                  :on-change #(conseq/tracking-state-number (-> % .-target .-value js/Number))}]]))

;;;;;;; topbar ns ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def topbar-menu-items
  [{:label "Themes"
    :children [{:label "Dark"
                :selected? (fn [state] (-> state :selected-theme (= ::themes/dark)))
                :on-click #(conseq/theme-is ::themes/dark)}
               {:label "Light"
                :selected? (fn [state] (-> state :selected-theme (= ::themes/light)))
                :on-click #(conseq/theme-is ::themes/light)}]}
   {:label "Settings"
    :children [{:label "Fact format"
                :icon ">"
                :children [{:label "Vector"
                            :selected? (fn [state] (-> state :fact-format (= :vector)))
                            :on-click #(precept.core/then [:settings :settings/fact-format :vector])}
                           {:label "Map"
                            :selected? (fn [state] (-> state :fact-format (= :map)))
                            :on-click #(precept.core/then [:settings :settings/fact-format :map])}]}]}])



(defn menu-child-menu-item [local-db i item]
  (let [selected?-predicate (:selected? item)
        on-click (:on-click item)]
    [:div {:style {:display "flex"
                   :cursor "default"}
           :on-click #(-> local-db (on-click))}
     (:label item)
     (when (selected?-predicate @local-db)
       " $")]))


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
                            "rgba(255,255,255,0.2)" "transparent")}
      :on-mouse-enter #(on-menu-item-mouse-enter local-db path props)}
     [:div {:style {:width 200 :cursor "default"}
            :on-click #(-> local-db (on-click))}
       (:label props)
       (when-let [icon (:icon props)]
         icon)
       (when-let [selected? (selected?-fn @local-db)]
         " $")]
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
  (let [open? (= i (first (:open-menus @local-db)))
        _ (println "header item" @local-db)]
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
            [menu-item local-db [i [i2]] props])
          children)])]))

(defn menu-header [topbar-menu-items settings theme]
  (let [local-db (r/atom (merge @settings @theme {:open-menus []}))
        _ (println "menu header theme" @theme)]
    [:div {:style {:display "flex"
                   :height 24
                   :vertical-align "middle"
                   :opacity 0.82
                   :background (:background-color (:theme @theme))}}
       (map-indexed
         (fn [i {:keys [label children]}]
           ^{:key i} [menu-header-item local-db i label children])
         topbar-menu-items)]))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
        [menu-header topbar-menu-items settings theme]]
       [:div
        [state-controls]]])))

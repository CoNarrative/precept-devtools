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
;(def topbar-state (r/atom {:open-menus []}))
;(def local-db (r/atom {:open-menus []}))
;(first (:open-menus @local-db))

;[0
; [0
;  [0 1])
(def topbar-menu-items
  [{:label "Themes"
    :children [{:label "Dark"
                :selected? (fn [state] (= ::themes/dark (:selected-theme @state)))
                :on-click #(precept.core/then [:themes :themes/selected ::themes/dark])}
               {:label "Light"
                :selected? (fn [state] (= ::themes/light (:selected-theme @state)))
                :on-click #(do (println "inserting light"[::themes ::themes/selected ::themes/light])
                             (precept.core/then [:themes :themes/selected ::themes/light]))}]}
   {:label "Settings"
    :children [{:label "Fact format"
                :icon ">"
                :children [{:label "Vector"
                            :selected? (fn [state] (= (:fact-format @state) :vector))
                            :on-click #(precept.core/then [:global :fact-format :vector])}
                           {:label "Map"
                            :selected? (fn [state] (= (:fact-format @state) :map))
                            :on-click #(precept.core/then [:global :fact-format :map])}]}]}])



(defn menu-child-menu-item [local-db i item]
  (let [selected?-predicate (:selected? item)
        on-click (:on-click item)]
    [:div {:style {:display "flex"
                   :cursor "default"}
           :on-click #(do (println "clicked")
                          (-> local-db (on-click)))}
     (:label item)]))


(defn menu-child-menu [local-db i items]
  [:div {:style {:position "absolute"
                 :background "rgba(49, 52, 57, .9)"
                 :padding "0px 20px"
                 :width 200}}
   (map-indexed
     (fn [i item]
       ^{:key i} [menu-child-menu-item local-db i item])
     items)])

(defn on-menu-item-mouse-enter [state path props]
  (println "menu item mouse enter")
  (swap! state assoc :hovered-item path)
  (when (:children props)
    (swap! state assoc :open-menus path)))

(defn on-menu-item-mouse-leave [state]
  (println "menu item mouse leave")
  (swap! state dissoc :hovered-item))

(defn menu-item [local-db path props]
  (let [open-menus (:open-menus @local-db)
        on-click (or (:on-click props) identity)]
    [:div
     {:style {:display "flex"
              :background (if (= path (:hovered-item @local-db))
                            "rgba(255,255,255,0.2)" "transparent")}
      :on-mouse-enter #(on-menu-item-mouse-enter local-db path props)}
      ;:on-click #(-> local-db (:on-click props))}
      ;:on-mouse-leave #(on-menu-item-mouse-leave local-db)}
     [:div {:style {:width 200 :cursor "default"}
            :on-click #(-> local-db (on-click))}
       (:label props)
       (when-let [icon (:icon props)]
         icon)]
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
     ;:on-mouse-leave #(on-header-item-mouse-leave local-db i)}
    label
    (when open?
      [:div {:style {:position "absolute"
                     :width 200
                     :background "rgba(49, 52, 57, .82)"
                     :display "flex"
                     :flex-direction "column"}}
        (map-indexed
          (fn [i2 props]
            [menu-item local-db [i [i2]] props])
          children)])]))

(defn menu-header [topbar-menu-items]
  (let [local-db (r/atom {:open-menus []})]
    (fn []
     [:div {:style {:display "flex"
                    :height 24
                    :vertical-align "middle"
                    :background "rgba(49, 52, 57, .82)"}}
        (map-indexed
          (fn [i {:keys [label children]}]
            ^{:key i} [menu-header-item local-db i label children])
          topbar-menu-items)])))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn header []
  (let [local-db (r/atom {})
        fact-format-sub (precept/subscribe [:fact-format])]
    (fn []
      (let [fact-format @fact-format-sub
            _ (println "fact format" fact-format)]
        [:div
         [:div {:style {:display "flex"}}
          [:div {:style {:padding-left 18
                         :padding-right 12}}
           [:img {:style {:width 18 :height 18 :vertical-align "middle" :margin-right 12}
                  :src "svg/precept-icon.svg"}]
           [:strong {:style {:vertical-align "middle"}}
            "Precept Devtools"]]
          [menu-header topbar-menu-items]]
         [:div
          [state-controls]]]))))

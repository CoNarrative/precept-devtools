(ns precept-visualizer.views.event-log
  (:require [precept-visualizer.state :as state]
            [precept-visualizer.views.explanations :as explanations]
            [reagent.core :as r]
            [precept-visualizer.icons :as icons]))

(def event-log-options (r/atom {:sort/ascending? true}))

(defn event-log-header []
  (let [{:sort/keys [ascending?]} @event-log-options]
    [:div {:style {:display "flex" :justify-content "flex-end"}}
     [:div {:on-click #(swap! event-log-options update :sort/ascending? not)}
      (if ascending?
        [icons/sort-ascending]
        [icons/sort-descending])]]))


(defn event-type-icon [event-type]
  (case event-type
    :add-facts
    [icons/plus {:style {:color "green"}}]
    :add-facts-logical
    [icons/plus-square {:style {:color "green"}}]
    :retract-facts
    [icons/minus {:style {:color "red"}}]
    :retract-facts-logical
    [icons/minus-square {:style {:color "red"}}]
    [:div]))

(defn event-log [theme]
  [:div
   [event-log-header]
   (->> (if (:sort/ascending? @event-log-options)
          @state/event-log
          (reverse @state/event-log))
        (group-by :state-number)
        (map
          (fn [[state-number events]]
            [:div {:key state-number}
             [:div {:style {:display "flex" :align-items "center" :justify-content "flex-end"}}
              [:strong {:style {:padding-right 6}}
               "Action"]
              [icons/flame {:style {:color "orange"}}]]
             (for [event events]
               [:div {:key   (:id event)
                      :style {:display "flex" :align-items "center"}}
                [:div {:style {:margin-right 20}}
                 [event-type-icon (:type event)]]
                [:div {:style {:flex 1}}
                 [explanations/explanation {:event             event
                                            :theme             theme
                                            :show-coordinates? false}]]])])))])

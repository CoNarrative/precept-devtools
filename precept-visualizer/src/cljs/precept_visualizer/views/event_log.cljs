(ns precept-visualizer.views.event-log
  (:require [precept-visualizer.state :as state]
            [precept-visualizer.views.explanations :as explanations]
            [reagent.core :as r]
            [precept-visualizer.icons :as icons]
            [precept.core :as precept]
            [goog.functions :refer [debounce throttle]]))


(defn event-log-header [{:sort/keys [ascending?]}]
  [:div {:style {:display "flex" :justify-content "flex-end"}}
   [:div {:on-click #(precept/then [:event-log :event-log/sort-ascending? (not ascending?)])}
    (if ascending?
      [:div "Recent last "
       [icons/sort-ascending]]
      [:div "Recent first "
       [icons/sort-descending]])]])


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

(def detect-near-bottom
  (debounce
    (fn []
      (when (< (- (.-scrollHeight js/document.body)
                  (+ (.-innerHeight js/window) (.-scrollY js/window)))
               100)
        (do (precept/then [:transient :event-log/near-bottom? true])
            (pr "near-bottom"))))
    50))

(defn event-log-state-id->number [id]
  (-> id (clojure.string/split #"-") last js/parseInt))


;; Can't be bothered right now...
(def ignore-scrolling? (atom nil))
;; ...Long story short scroll listeners receive events applied to document and window regardless of where they are dispatched to,
;; and I can't otherwise scroll programmatically without triggering every scroll listener

(defn detect-ids-in-view [ids]
  (throttle
    (fn [e]
      (when-not @ignore-scrolling?
        (->> ids
             (map
               (fn [id]
                 (let [el (.getElementById js/document id)
                       rect (.getBoundingClientRect el)
                       top (.-top rect)
                       bottom (.-bottom rect)
                       in-view? (and (< top (.-innerHeight js/window))
                                     (>= bottom 0))]
                   (when in-view?
                     (do (pr "dispatching state in view" id)
                         id)))))
             (remove nil?)
             (map event-log-state-id->number)
             (conj [:event-log :event-log/states-in-view])
             (precept/then))))
    50))

(defn ->event-log-state-dom-id [n] (str "event-log-state-" n))

(defn sort-numbers [ns ascending?]
  (if ascending?
    (sort ns)
    (sort #(compare %2 %1) ns)))

(defn event-log [theme]
  (let [{:sort/keys [ascending?]} @(precept/subscribe [:event-log])
        log @state/event-log
        grouped (group-by :state-number log)
        state-numbers (sort-numbers (keys grouped) ascending?)
        detect-states-in-view (detect-ids-in-view (map ->event-log-state-dom-id state-numbers))
        _ (.addEventListener js/document "scroll" detect-states-in-view)]
    (r/with-let [_ (.addEventListener js/document "scroll" detect-near-bottom)]
      [:div
       [event-log-header {:sort/ascending? ascending?}]
       (->> state-numbers
            (map
              (fn [state-number]
                (let [events (get grouped state-number)]
                  [:div {:id  (->event-log-state-dom-id state-number)
                         :key state-number
                         :style {:margin "15px 0px"}}
                   [:div {:style {:display "flex" :align-items "center"
                                  :justify-content "center" :margin-left 44}}

                    [icons/flame {:style {:color "orange"}}]]
                   (for [event events]
                     [:div {:key   (:id event)
                            :style {:display "flex" :align-items "center" :margin-bottom 15}}
                      [:div {:style {:margin-right 20}}
                       [event-type-icon (:type event)]]
                      [:div {:style {:flex 1}}
                       [explanations/explanation {:event             event
                                                  :theme             theme
                                                  :show-coordinates? true}]]])]))))]
      (finally (.removeEventListener js/document "scroll" detect-near-bottom)
               (.removeEventListener js/document "scroll" detect-states-in-view)))))

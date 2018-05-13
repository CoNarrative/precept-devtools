(ns precept-visualizer.rules.event-log
  (:require-macros [precept.rules :refer [rule define session defsub]])
  (:require [precept.core :as precept]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept-visualizer.ajax :as ajax]
            [precept-visualizer.views.event-log :refer [ignore-scrolling?]]
            [precept-visualizer.state :as state]
            [precept-visualizer.util :as vis-util]))


(def states-per-page 5)

(rule fetch-part-of-event-log-on-event-log-tab-select
  {:group :action}
  [[:transient :select-tab :event-log]]
  [[:global :tracking/state-number ?cur]]
  [[_ :max-state-number ?max]]
  =>
  (let [range' (if (= ?cur ?max)
                 (take 5 (reverse (range (inc ?max))))
                 (if (pos? (- ?cur 2))
                   (take 5 (range (- ?cur 2)))
                   (range 5)))]
    (ajax/update-event-log! ((juxt first last) (sort range')))))

(rule update-fetched-states
  [?req <- [:transient :update-state-numbers ?received]]
  [[:event-log :event-log/fetched-states ?existing]]
  =>
  (retract! ?req)
  (insert-unconditional! [:event-log :event-log/fetched-states (conj ?existing ?received)]))

;(rule do-scroll-to-state-in-event-log
;  {:group :action}
;  [[:transient :tracking/changed-state-number-to ?n]]
;  [[:tabs :tabs/selected :event-log]]
;  =>
;  (insert-unconditional! [:transient :scrolling-programmatically? true])
;  (pr "do scroll-to" ?n))
;;
;(rule event-log-syncs-tracking-state-with-states-showing-in-browser-window-on-scroll
;  {:group :action}
;  [:not [:transient :scrolling-programmatically? true]]
;  [[:tabs :tabs/selected :event-log]]
;  ;[[:global :tracking/state-number ?cur]] ;; loops
;  [[:event-log :event-log/states-in-view ?states-in-view]]
;  [[:event-log :event-log/sort-ascending? ?sort-ascending]]
;  [:test (seq ?states-in-view)]
;  =>
;  (let [next (if ?sort-ascending
;               (last (sort ?states-in-view))
;               (first (sort ?states-in-view)))]
;    #_(if (not= ?cur next))
;    (pr "autochanging state based on scroll " next)
;    #_(insert-unconditional! [:global :tracking/state-number next])))

(rule event-log-scrolls-to-entry-in-browser-when-tracking-state-changed
  {:group :action}
  [[:transient :tracking/changed-state-number-to ?n]]
  [[:tabs :tabs/selected :event-log]]
  =>
  (let [dom-id (str "event-log-state-" ?n)]
    (when-let [dom-el (.getElementById js/document dom-id)]
      (let [rect (.getBoundingClientRect dom-el)
            top (.-top rect)
            _ (pr "scrolling to " ?n top)]
        (reset! ignore-scrolling? true)
        (.setTimeout js/window
                     #(reset! ignore-scrolling? false)
                     200)
        (.scrollIntoView dom-el)))))


(defn get-uncached-event-range [cur existing ascending?]
  (let [requested (if ascending?
                    [cur (+ cur states-per-page)]
                    [(- cur states-per-page) cur])]
    (->> (range (first requested) (inc (second requested)))
         (remove (set existing))
         (vec)
         ((juxt first last)))))

(defn get-ultimate-event-in-view [in-view ascending?]
  (if ascending?
    (apply max in-view)
    (apply min in-view)))

(defn append-to-log [ascending?]
  (comp
    #(if ascending?
       (sort-by :state-number %)
       (sort (fn [a b] (compare (:state-number b) (:state-number a))) %))
    (partial vis-util/distinct-by :id)
    concat))

(rule fetch-next-log-entries-when-near-bottom-if-not-cached
  [[:tabs :tabs/selected :event-log]]
  [:not [:transient :select-tab :event-log]]
  [?evt <- [:transient :event-log/near-bottom?]]
  [[:event-log :event-log/sort-ascending? ?ascending]]
  [[:event-log :event-log/states-in-view ?in-view]]
  [[:event-log :event-log/fetched-states ?fetched]]
  [[_ :max-state-number ?max]]
  =>
  (let [cur (get-ultimate-event-in-view ?in-view ?ascending)
        next-range (get-uncached-event-range cur ?fetched ?ascending)]
    (retract! ?evt)
    (-> (ajax/get-log-entries-for-state-range next-range)
        (.then
          (fn [entries]
            (let [events (reduce concat entries)
                  state-numbers (map :state-number events)]
              (do (swap! state/event-log (append-to-log ?ascending) events)
                  (precept/then [:transient :update-state-numbers state-numbers]))))))))

(defsub :event-log
  [[:event-log :event-log/sort-ascending? ?bool]]
  =>
  {:sort/ascending? ?bool})

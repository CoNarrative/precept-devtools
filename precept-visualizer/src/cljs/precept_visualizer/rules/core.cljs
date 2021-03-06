(ns precept-visualizer.rules.core
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept.accumulators :as acc]
            [precept-visualizer.themes :as themes]
            [precept-visualizer.schema :refer [db-schema client-schema]]
    ;; TODO. Rename other-rules ns
            [precept-visualizer.rules.rule-tracking]
            [precept-visualizer.rules.fact-tracking]
            [precept-visualizer.rules.event-log]
            [precept-visualizer.other-rules]))


(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [{:db/id          :global
                           :tracking/sync? true
                           :view/mode      :diff}
                          {:db/id                            :settings
                           :settings/selected-theme-id       ::themes/light
                           :settings/fact-format             :vector
                           :settings/state-controls-visible? true}
                          {:db/id                 :windows
                           :state-controls/height "6vh"}
                          {:db/id         :tabs
                           :tabs/selected :diff}
                          {:db/id                     :event-log
                           :event-log/fetched-states  #{}
                           :event-log/sort-ascending? false}
                          (merge {:db/id ::themes/dark} themes/dark)
                          (merge {:db/id ::themes/light} themes/light)]))


(rule when-sync-tracking-latest-state
  {:group :action}
  [[:global :tracking/sync? true]]
  [[_ :max-state-number ?max]]
  =>
  (insert-unconditional! [:global :tracking/state-number ?max]))


(rule tab-selected-when-select-tab
  {:group :action}
  [[:transient :select-tab ?tab]]
  =>
  (insert-unconditional! [:tabs :tabs/selected ?tab]))

(define [:global :max-state-number ?n] :- [?n <- (acc/max :v) :from [_ :state/number]])


(define [:windows :explanations/width-percent 0] :- [:not [_ :fact-tracker/fact-e]])


(define [:windows :explanations/width-percent 50] :- [:exists [_ :fact-tracker/fact-e]])


(define [:windows :main/width-percent (- 100 ?explanation-width)]
        :- [[:windows :explanations/width-percent ?explanation-width]])

(define [:windows :state-controls/visible? ?bool] :- [[:settings :settings/state-controls-visible? ?bool]])


(defsub :selected-theme
  [[:settings :settings/selected-theme-id ?theme-id]]
  [?kvs <- (acc/all (juxt :a :v)) :from [?theme-id :all]]
  =>
  {:selected-theme ?theme-id
   :theme (into {} ?kvs)})


(defsub :settings
  [[:settings :settings/selected-theme-id ?theme-id]]
  [[:settings :settings/fact-format ?fact-format]]
  [[:settings :settings/state-controls-visible? ?state-controls-visible]]
  =>
  {:theme-id ?theme-id
   :fact-format ?fact-format
   :state-controls-visible? ?state-controls-visible})


(defsub :header
  [[_ :tracking/state-number ?n]]
  [[_ :tracking/sync? ?bool]]
  [[_ :max-state-number ?max]]
  =>
  {:tracking/sync? ?bool
   :tracking/state-number ?n
   :max-state-number ?max})


(rule derive-action-fact-ids
  [[?state-id :state/number ?state-number]]
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/action true]]
  [?action-fact-ids <- (acc/all :v) :from [?event-id :event/facts]]
  ;[(<- ?action-facts (entities ?action-fact-ids))]
  =>
  (let [action-id (util/guid)]
    (insert! [action-id :action/state-number ?state-number])
    (doseq [fact-id ?action-fact-ids]
      (insert! [action-id :action/fact-id fact-id]))))

(rule derive-action-facts
  [[?action-id :action/fact-id ?fact-id]]
  [[?fact-id :fact/string ?fact-str]]
  =>
  (insert! [?action-id :action/fact-string ?fact-str]))

(defsub :actions
  [[_ :tracking/state-number ?n]]
  [[?action-id :action/state-number ?n]]
  [?action-fact-strs <- (acc/all :v) :from [?action-id :action/fact-string]]
  =>
  {:results (mapv cljs.reader/read-string ?action-fact-strs)})

(defsub :diff-view
  [[:global :view/mode :diff]]
  ;; just marshalling for now, nothing derived. Move to upstream rule
  ;; once enriching
  [[_ :tracking/state-number ?n]]
  [[?state :state/number ?n]]
  [[?state :state/added ?added]]
  [[?state :state/removed ?removed]]
  =>
  {:state/added ?added
   :state/removed ?removed})


(defsub :state-tree
  [[_ :tracking/state-number ?n]]
  =>
  {:state/number ?n})


(defsub :windows
  [(<- ?windows (entity :windows))]
  =>
  {:payload ?windows})


(defsub :tabs
  [[:tabs :tabs/selected ?selected-tab]]
  =>
  {:selected-tab ?selected-tab})


(session visualizer-session
  'precept-visualizer.rules.core
  'precept-visualizer.rules.rule-tracking
  'precept-visualizer.rules.fact-tracking
  'precept-visualizer.rules.event-log
  'precept-visualizer.other-rules
  :db-schema db-schema
  :client-schema client-schema
  :reload true)



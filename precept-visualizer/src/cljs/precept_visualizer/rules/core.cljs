(ns precept-visualizer.rules.core
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept.accumulators :as acc]
            [precept-visualizer.themes :as themes]
            [precept-visualizer.schema :refer [db-schema client-schema]]
            [precept-visualizer.event-parser :as event-parser]
    ;; TODO. Rename other-rules ns
            [precept-visualizer.rules.rule-tracking]
            [precept-visualizer.rules.fact-tracking]
            [precept-visualizer.other-rules]
            [precept-visualizer.ws :as ws]))


;; FIXME. Duplicate definition in rules.rule-tracking
;; When the session is defined in another namespace, rules in this ns that use this fn
;; don't get its definition. Hoping this is fixed by https://github.com/cerner/clara-rules/issues/359,
;; https://github.com/CoNarrative/precept/issues/111
(defn sort-rule-history-tracker-events [history-event-entities]
  (-> history-event-entities
      (->> (clojure.walk/postwalk (fn [x] (if (record? x) (into {} x) x))))
      (event-parser/ast->datomic-maps #{} {:trim-uuids? false})
      (->> (reduce concat))
      (->> (sort
             (fn [a b] (if (> (:rule-history.event/state-number a)
                              (:rule-history.event/state-number b))
                         1
                         (if (not= (:rule-history.event/state-number a)
                                   (:rule-history.event/state-number b))
                           -1
                           (if (> (:rule-history.event/event-number a)
                                  (:rule-history.event/event-number b))
                             1
                             -1))))))))


(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [{:db/id :global
                           :tracking/sync? true
                           :view/mode :diff}
                          {:db/id :settings
                           :settings/selected-theme-id ::themes/light
                           :settings/fact-format :vector}
                          (merge {:db/id ::themes/dark} themes/dark)
                          (merge {:db/id ::themes/light} themes/light)]))


(rule when-sync-tracking-latest-state
  {:group :action}
  [[:global :tracking/sync? true]]
  [[_ :max-state-number ?max]]
  =>
  (insert-unconditional! [:global :tracking/state-number ?max]))


(define [:global :max-state-number ?n] :- [?n <- (acc/max :v) :from [_ :state/number]])


(define [:windows :explanations/width-percent 0] :- [:not [_ :explaining/fact]])


(define [:windows :explanations/width-percent 50] :- [:exists [_ :explaining/fact]])


(define [:windows :main/width-percent (- 100 ?explanation-width)]
        :- [[:windows :explanations/width-percent ?explanation-width]])


(defsub :selected-theme
  [[:settings :settings/selected-theme-id ?theme-id]]
  [?kvs <- (acc/all (juxt :a :v)) :from [?theme-id :all]]
  =>
  {:selected-theme ?theme-id
   :theme (into {} ?kvs)})


(defsub :settings
  [[:settings :settings/selected-theme-id ?theme-id]]
  [[:settings :settings/fact-format ?fact-format]]
  =>
  {:theme-id ?theme-id
   :fact-format ?fact-format})


(defsub :header
  [[_ :tracking/state-number ?n]]
  [[_ :tracking/sync? ?bool]]
  [[_ :max-state-number ?max]]
  =>
  {:tracking/sync? ?bool
   :tracking/state-number ?n
   :max-state-number ?max})


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



(session visualizer-session
  'precept-visualizer.rules.core
  'precept-visualizer.rules.rule-tracking
  'precept-visualizer.rules.fact-tracking
  'precept-visualizer.other-rules
  :db-schema db-schema
  :client-schema client-schema
  :reload true)



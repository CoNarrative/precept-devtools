(ns precept-visualizer.rules
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept.accumulators :as acc]
            [precept.orm :as orm]
            [precept-visualizer.state :as state]
            [precept-visualizer.schema :refer [db-schema client-schema]]
            [precept-visualizer.ws :as ws]))

(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [[:global :tracking/sync? true]
                          [:global :view/mode :diff]]))

(rule on-clear-all-explanations
  {:group :action}
  [[:transient :clear-all-explanations true]]
  [?explanations <- (acc/all) :from [_ :explaining/fact]]
  =>
  (retract! ?explanations))


(rule on-explain-request
  {:group :action}
  [[:transient :explanation/request ?fact-str]]
  [:not [_ :explaining/fact ?fact-str]]
  =>
  (insert-unconditional! [(util/guid) :explaining/fact ?fact-str]))


(rule explain-fact-for-events-in-state
  ;; when explaining a fact
  [[?explanation-id :explaining/fact ?fact-str]]
  ;; and we are tracking a state number
  [[:global :tracking/state-number ?current-state-number]]

  ;; and current state has event with that fact's value
  [[?state-id :state/number ?current-state-number]]
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/facts ?fact-id]]
  [[?fact-id :fact/string ?fact-str]]
  [[?event-id :event/number ?event-number]]
  ;; when no explanation exists for a fact mentioned in event of current state
  [:not [?event-id :event/log-entry]]

  =>
  ;; get event object to generate explanation
  (ws/get-log-entry-by-coords [?current-state-number ?event-number]))


(rule max-state-number
  [?n <- (acc/max :v) :from [_ :state/number]]
  =>
  (insert! [:global :max-state-number ?n]))

(rule when-sync-tracking-latest-state
  [[:global :tracking/sync? true]]
  [[_ :max-state-number ?max]]
  =>
  (insert-unconditional! [:global :tracking/state-number ?max]))

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

; TODO. max history of fact default to 5?
;(rule print-added-fact-info-1
;  [[_ :tracking/state-number ?state-number]]
;  [[?state :state/number ?state-number]]
;  ; a. should be one to many. can always accumulate if need list, can't easily match otherwise
;  ; b. should have fact ids instead of strings that tie to events, which are unique owing to
;  ; their temporality unlike any lone representation of a fact we currently have (the same fact
;  ; may be added and removed at different points in time)
;
;  [[?state :state/added-facts ?fact-id]]
;  [[?state :state/events ?event]]
;  [[?event :event/facts ?fact-id]]
;
;  ; Log id can allow reference to auxillary data (dtos)
;  ; but not clear whether dtos have enough info to be useful a. by themselves or b. using rules
;  [[?log-id :log/event-id ?event-id]]
;  ;[?fact <- (acc/all) :from [?fact-id :all]]
;
;  ; Enrich insertion with event number
;  [[?event :event/number ?event-number]]
;  =>
;  '(get-log-id ?log-id))

(rule form-event-explanation-when-requested-and-log-entry
  [[?explanation-id :explaining/fact ?fact-str]]
  ;; and we are tracking a state number
  [[:global :tracking/state-number ?current-state-number]]

  ;; and current state has event with that fact's value
  [[?state-id :state/number ?current-state-number]]
  [[?state-id :state/events ?event-id]]
  [[?event-id :event/facts ?fact-id]]
  [[?fact-id :fact/string ?fact-str]]
  [[?event-id :event/log-entry ?log-entry]]
  =>
  ;; Because this is already a data representation of an event there's not
  ;; much we can do to make it into an explanation here. View will format as needed
  ;; Note: request-id isn't effectively used -- could make inserted fact one-to-many
  ;; and get rid of it
  ;; We are performing this insertion so we can accumulate all explanations
  ;; in a subscription. Logically it's not necessary; we could just have this in the
  ;; sub if we had better matching and marshalling capability with lists
  ;; note :event/log-entry is a base fact, pulled from server on demand and
  ;; kept in memory. This duplicate of it should come and go based upon the
  ;; presence of an explanation request, hence insertL here. :explanation
  ;; implies "active"
  (insert! [?explanation-id :explanation/log-entry ?log-entry]))

(defsub :explanations
  [?explanations <- (acc/all :v) :from [_ :explanation/log-entry]]
  =>
  {:payload ?explanations})



(session visualizer-session
  'precept-visualizer.rules
  :db-schema db-schema
  :client-schema client-schema
  :reload true)

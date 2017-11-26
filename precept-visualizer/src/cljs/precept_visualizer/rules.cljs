(ns precept-visualizer.rules
  (:require-macros [precept.dsl :refer [<- entity entities]])
  (:require [precept.rules :refer [rule define session defsub]]
            [precept.util :refer [insert! retract! insert-unconditional!] :as util]
            [precept.accumulators :as acc]
            [precept.orm :as orm]
            [precept-visualizer.mouse :as mouse]
            [precept-visualizer.state :as state]
            [precept-visualizer.schema :refer [db-schema client-schema]]
            [precept-visualizer.ws :as ws]))

(rule initial-facts
  {:group :action}
  [[:transient :start true]]
  =>
  (insert-unconditional! [[:global :tracking/sync? true]
                          [:global :view/mode :diff]
                          [:state-tree :col/id :e]
                          [:state-tree :col/id :a]
                          [:state-tree :col/id :v]
                          ; Potentially disasterous eids!
                          [:e :col/width 200]
                          [:a :col/width 200]
                          [:v :col/width 200]
                          [:state-tree :col/order [:e :a :v]]]))

(rule on-clear-all-explanations
  {:group :action}
  [[:transient :clear-all-explanations true]]
  [?explanations <- (acc/all) :from [_ :explaining/fact]]
  =>
  (retract! ?explanations))

;; FIXME. Does Clara support fact-binding in :or conditions?
;; Our boolean syntax wouldn't easily allow for a fact binding, because
;; we expect a single vector to be a positional match instead of [[]]
;; We compile to this AST which is wrong. Should throw an error if anything:
;  [:or [?fact <- [:transient ::mouse/mouse-down]]
;       [?fact <- [:transient ::mouse/mouse-up]]
;       [?fact <- [:transient ::mouse/mouse-move]]))
;{:ns-name precept-visualizer.rules,
; :lhs [[:or
;        {:type :all, :constraints [(= ?fact (:e this))]}
;        {:type :all, :constraints [(= ?fact (:e this))]}
;        {:type :all, :constraints [(= ?fact (:e this))]}]],
; :rhs (do (do (do (println "Mouse fact rec'd" ?fact)))),
; :props {:group :action},
; :name "precept-visualizer.rules/detect-mouse-ns-facts"}


(rule detect-mouse-ns-facts
  {:group :action}
  [:or [:transient ::mouse/mouse-down ?v]
       [:transient ::mouse/mouse-up ?v]
       [:transient ::mouse/mouse-move ?v]]
  =>
  (println "Mouse fact rec'd" ?v))


;; Derive column's index from column order
(define
  [?col-id :col/index (.indexOf ?col-order ?col-id)]
  :- [[:state-tree :col/order ?col-order]]
     [[:state-tree :col/id ?col-id]])

; Problem: The "base case" below doesn't match, so this rule loops.
; Clara's implementation may result in the rule continuing to fire
; without knowledge of the facts it inserts...?
; Side note:
; In place of an engine level modify we could accumulate facts inside
; a (modify! ...) fn and rewrite the LHS to include [:not [:exists [?modified-fact]]] iff
; we can prevent a loop with a `not exists` condition that only obtains
; for the first firing for a rule's match
;
; Log output:
; No modify exists
; ;mouse moving 2
; rules.cljs:267 resize! :a
; client.cljs:34 Caught modify
;     #precept.util.Tuple{:e :transient, :a :modify, :v true, :t 689}
; 2705 rules.cljs:267 resize! :a <- loop

;(rule catch-a-modify-fact
;  {:group :action}
;  [?fact <- [:transient :modify true]]
;  =>
;  (println "Caught modify" ?fact))
;
;(rule not-a-modify-fact-present
;  {:group :action}
;  [:not [:exists [:transient :modify true]]]
;  =>
;  (println "No modify exists"))
;
;(rule on-state-tree-columns-resized
;  {:group :action}
;  ;; When the event
;  [?wc-id <- [:transient :col.width-changed/col-id ?col-id]]
;  [?wc-delta <- [:transient :col.width-changed/delta ?d]]
;
;  ;; Base case
;  [:not [:exists [:transient :modify true]]]
;
;  ;; Width and index of the column
;  [[?col-id :col/width ?col-width]]
;  [[?col-id :col/index ?i]]
;
;  ;; Left neighbor column (only edges can be resized)
;  [[?left-col-id :col/index (dec ?i)]]
;  [[?left-col-id :col/width ?left-col-width]]
;  =>
;  (insert-unconditional! [:transient :modify true])
;  (.log js/console (str "modify resize!" ?col-id))
;  ;; The column is its width + the delta (pos or neg)
;  ;; The left neighbor is its width + the delta's inverse
;  (insert-unconditional! [[?col-id :col/width (+ ?col-width ?d)]
;                          [?left-col-id :col/width (+ ?left-col-width (- ?d))]]))
;
;(rule on-column-mouse-up
;  {:group :action}
;  ;; When mouse up
;  [[:transient :state-tree.col/mouse-up-on-col ?col-id]]
;  ;; And mouse down facts exist
;  [?mouse-down-col <- [:state-tree :state-tree.col/mouse-down-on-col ?col-id]]
;  [?mouse-down-evt <- [:state-tree :state-tree.col/mouse-down-evt ?evt]]
;  =>
;  (.log js/console "Mouseup")
;  ;; Mouse isn't down
;  (doseq [x [?mouse-down-col ?mouse-down-evt]]
;    (retract! x)))
;
;
;(rule on-column-mouse-move
;  {:group :action}
;  [[:transient :state-tree.col/mouse-move-evt ?move-evt]]
;  [[:transient :state-tree.col/mouse-move-on-col ?col-id]]
;
;  [[:state-tree :state-tree.col/mouse-down-on-col ?col-id]]
;  [[:state-tree :state-tree.col/mouse-down-evt ?down-evt]]
;  =>
;  (.log js/console "mouse moving" (apply - (mapv #(.-clientX %) [?move-evt ?down-evt])))
;  (insert-unconditional! [[:transient :col.width-changed/col-id ?col-id]
;                          [:transient :col.width-changed/delta
;                           (apply - (mapv #(.-clientX %) [?move-evt ?down-evt]))]]))


;; TODO. Not sure if we should pull all eids associated with the
;; fact-str or leave them. No apparent problems with leaving them (current approach)
(rule on-clear-explanation
  {:group :action}
  [[:transient :stop-explain-fact-requested ?fact-str]]
  [?fact-exp <- [_ :explaining/fact ?fact-str]]
  =>
  (retract! ?fact-exp))


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
  (insert! [?explanation-id :explanation/log-entry {:fact-str ?fact-str
                                                    :event ?log-entry}]))

(defsub :explanations
  [?explanations <- (acc/all :v) :from [_ :explanation/log-entry]]
  =>
  {:payload ?explanations})



(session visualizer-session
  'precept-visualizer.rules
  :db-schema db-schema
  :client-schema client-schema
  :reload true)

(ns precept-visualizer.views.explanations
  (:require [precept.core :as precept]
            [precept-visualizer.util :as util]
            [precept-visualizer.event-parser :as event-parser]
            [precept-visualizer.matching :as matching]
            [precept-visualizer.views.consequents :as conseq]
            [net.cgrand.packed-printer :as packed]))


(defn by-state-events [state-direction event-direction]
  (fn [& vals]
    (let [c (cond-> (apply compare (map first vals))
                    (= state-direction :desc) -
                    true identity)]
      (if (not= c 0)
        c
        (cond-> (apply compare (map second vals))
                (= event-direction :desc) -
                true identity)))))


(defn close-explanation-button [fact-str]
  [:div {:style {:display "flex" :justify-content "flex-end"}}
   [:span {:style
                     {:width 20
                      :height 20
                      :position "relative"
                      :top -24
                      :right -24
                      :background "white"
                      :borderRadius "100%"
                      :cursor "pointer"
                      :display "flex" :justify-content "center" :align-items "center"}
           :on-click #(conseq/stop-explain-fact-requested fact-str)}
    [:span {:style {:color "red"}}
     "x"]]])


(defn event-coordinates-row [state-number event-number theme]
  [:div {:style {:margin-bottom   "20px"
                 :display         "flex"
                 :justify-content "space-between"}}
   [:span {:class "label black"}
    (str "State " state-number)]
   [:span {:class "label outline"
           :style {:color (:text-color theme)}}
    (str "Event " event-number)]])


(defn schema-enforcement-explanation [caused-by-insert index-path conflict]
  (if caused-by-insert
    [:div (str "Caused by insert " (util/display-eav caused-by-insert)
               " at index path " index-path)]
    [:div
     [:div {:class "label tag"}
      (str "Replaced")]
     [:pre (str (util/display-eav conflict))]

     [:div
      [:span {:class "label badge focus"}
       (str "Enforced schema rules")]
      [:br]

      [:div {:class "label tag"}
       "Attribute"]
      [:pre (str (last index-path))]

      [:div {:class "label tag"}
       "Cardinality:"]
      [:pre (str (first index-path))]]]))


(defn action-explanation [{:keys [event fact-str]} theme]
  (let [{:keys [state-number event-number facts type]} event
        {:keys [schema/activated schema/caused-by-insert schema/conflict schema/index-path]}
        event
        fact-edn (cljs.reader/read-string fact-str)
        fact-format (:fact-format @(precept/subscribe [:settings]))]
    [:div {:class "example"
           :style {:display "flex"
                   :flex-direction "column"}}
     [close-explanation-button fact-str]
     [event-coordinates-row state-number event-number theme]
     [:div
      [:span {:class "label badge error"}
       (util/event-types->display type)]]
     [:pre {:style {:background (:pre-background-color theme)}}
      (matching/format-edn-str
        (event-parser/prettify-all-facts
          (first (filter #{fact-edn} facts))
          {:trim-uuids? true :format fact-format}))]
     (when activated
       [schema-enforcement-explanation caused-by-insert index-path conflict])]))


(defn rule-type-from-name [name]
  (cond
    (clojure.string/includes? name "-sub___impl")
    (let [clean-name (clojure.string/replace name "-sub___impl" "")]
      {:name clean-name
       :type :sub
       :label "Subscription"
       :class-names ["focus"]})

    (clojure.string/includes? name "define-")
    {:name name
     :type :define
     :label "Define"
     :class-names ["success"]}

    :else
    {:name name
     :type :rule
     :label "Rule"
     :class-names ["focus"]}))


(defn rule-name [name theme]
  (let [data (rule-type-from-name name)]
    [:div {:style {:margin-bottom "20px"}}
     [:span {:class (clojure.string/join " "
                      (into ["label"]
                        (conj (:class-names data) "outline")))}
      (:label data)]
     [:kbd {:style {:color (:text-color theme)
                    :padding-left 12}}
      (str (:name data))]]))


(defn subscription-consequence-explanation [consequence-op facts colors]
  (let [sub-map (:v (first facts))]
    [:div
     [:span {:class "label badge error"}
      (str "Subscription result / " (util/event-types->display consequence-op))]
     [:pre
      "{"
      (map
        (fn [[k v]]
          [:span {:key (str k v)}
           (str k " ")
           [matching/pattern-highlight-fact v colors]])
        sub-map)
      "}"]]))


(defn consequence-explanation [rule-name consequence-op facts colors]
  (let [rule-data (rule-type-from-name rule-name)
        rule-type (:type rule-data)]
    (if (= rule-type :sub)
      [subscription-consequence-explanation consequence-op facts colors]
      [:div
       [:span {:class "label badge error"}
        (util/event-types->display consequence-op)]
       [:pre
        (for [fact facts]
          ^{:key (str fact)}
          [matching/pattern-highlight-fact
           fact
           colors])]])))


(defn conditions-row [eav-conditions colors theme]
  [:div
   [:span {:class "label tag"
           :style {:color (:text-color theme)}}
    "Conditions"]
   [matching/pattern-highlight eav-conditions colors]])


(defn matches-row [matches colors theme]
  [:div
   [:span {:class "label tag"
           :style {:color (:text-color theme)}}
    (if (> (count matches) 1) "Matches" "Match")]
   [:pre
    (for [match (->> matches
                     (mapv first)
                     (remove event-parser/has-attribute-of-ignored-fact?))]
      [:span {:key (str match)}
       [matching/pattern-highlight-fact
        match
        colors]
       [:br]])]])


(defn rule-explanation [{:keys [event fact-str] :as payload} theme]
  (let [{:keys [lhs bindings name type matches display-name rhs state-number event-number
                facts props ns-name]} event
        eav-conditions (event-parser/lhs->eav-syntax lhs)
        colors (matching/eav-conditions->colors eav-conditions bindings)]
    [:div {:class "example"
           :style {:display "flex"
                   :flex-direction "column"}}
     [close-explanation-button fact-str]
     [event-coordinates-row state-number event-number theme]
     [rule-name name theme]
     [:div
      [conditions-row eav-conditions colors theme]
      [matches-row matches colors theme]
      [consequence-explanation name type facts colors]]]))


(defn explanation [{:keys [event fact-str] :as payload} theme]
  (cond
    (nil? event)
    nil

    (#{:add-facts :retract-facts} (:type event))
    ^{:key (:fact-str payload)} [action-explanation payload theme]

    :default
    [rule-explanation payload theme]))


(defn prev-next-rule-history-buttons [rule-name selected-event-index total-event-count theme]
  (let [has-prev? (> selected-event-index 0)
        has-next? (> (dec total-event-count) selected-event-index)]
    [:div {:style {:display "flex" :justify-content "space-between"}}
     [:div
      {:style    {:cursor      "pointer"
                  :user-select "none"
                  :color       (if has-prev? (:text-color theme) (:disabled-text-color theme))}
       :on-click #(when has-prev? (conseq/viewing-rule-history-event (str rule-name) (dec selected-event-index)))}
      "<-"]
     [:div
      {:style    {:cursor      "pointer"
                  :user-select "none"
                  :color       (if has-next? (:text-color theme) (:disabled-text-color theme))}
       :on-click #(when has-next? (conseq/viewing-rule-history-event (str rule-name) (inc selected-event-index)))}
      "->"]]))


(defn rule-tracker [rule-name
                    {:keys [log-entry selected-event-index total-event-count] :as history}
                    theme]
 [:div
  [prev-next-rule-history-buttons rule-name selected-event-index total-event-count theme]
  (when log-entry
    [rule-explanation {:event log-entry} theme])])


(defn explanations [theme windows]
  (let [{:keys [payload]} @(precept/subscribe [:explanations])
        sort-states :desc
        sort-events :desc]
    (if (empty? payload)
      nil
      [:div {:style {:position "fixed"
                     :overflow-y "scroll"
                     :top 0
                     :width (str (or (:explanations/width-percent windows) "100") "vw")
                     :height "100%"
                     :right 0
                     :background (:background-color theme)}}
       [:div {:style {:display "flex" :flex-direction "column"}}
        [:div {:style {:display "flex"}}
          [:button {:style {:background (:primary theme)}
                    :on-click #(conseq/explanations-cleared)}
           "Clear all"]]
        [:div {:style {:height "25px"}}]
        [:div {:style {:display "flex" :flex-direction "row"}}
         [:div {:style {:min-width "15px"}}]
         [:div {:style {:width "100%" :display "flex" :flex-direction "column"}}
          (for [x (->> payload (sort-by (comp (juxt :state-number :event-number) :event)
                                        (by-state-events sort-states sort-events)))]
            [:div {:key (:fact-str x)
                   :style {:margin "15px 0px"}}
             [explanation x theme]])]
         [:div {:style {:min-width "15px"}}]]]])))

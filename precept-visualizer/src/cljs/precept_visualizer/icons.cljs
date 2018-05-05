(ns precept-visualizer.icons)

(defn glyph-icon [{:keys [style class on-click]}]
  [:i {:class    class
       :style    (merge {:height 24 :width 24 :font-size 24} style)
       :on-click on-click}])

(defn arrow-circle-left [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-arrow-circle-left")])

(defn arrow-circle-right [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-arrow-circle-right")])

(defn check [{:keys [style]}]
  [:div {:style (merge {:height 24 :width 24 :font-size 24} style)}
   "✅"])

(defn right-caret [{:keys [style]}]
  [:div {:style (merge {:height 24 :width 24 :font-size 24} style)}
   "▶︎"])

(defn clock [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "far fa-clock")])

(defn eye [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-eye")])

(defn align-left [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-align-left")])

(defn sort-ascending [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-sort-up")])

(defn sort-descending [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-sort-down")])

(defn flame [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-burn")])

(defn plus [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-plus")])

(defn plus-square [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "far fa-plus-square")])

(defn minus [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-minus")])

(defn minus-square [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "far fa-minus-square")])

(defn left-arrow-circle [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-arrow-circle-left")])

(defn right-arrow-circle [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-arrow-circle-right")])

(defn left-arrow [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-arrow-left")])

(defn right-arrow [{:keys [style] :as props}]
  [glyph-icon (assoc props :class "fas fa-arrow-right")])

(comment
  (def rule-to-explain {:bindings     {:?fact {:v 18, :e :transient, :t 32, :a :input/key-code}},
                        :name         "precept.impl.rules/clean-transients___impl",
                        :type         :retract-facts,
                        :ns-name      'precept.impl.rules,
                        :lhs
                                      '[{:constraints  [(= :transient (:e this))],
                                         :type         :all,
                                         :fact-binding :?fact}],
                        :event-number 2,
                        :matches      [[{:v 18, :e :transient, :t 32, :a :input/key-code} 52]],
                        :id           #uuid "bd656dc1-418c-4237-9b4e-659dbf7cee79",
                        :display-name "clean-transients___impl",
                        :state-id     #uuid "31e1d94f-53d5-4f9b-ae7b-4f268a7563e3",
                        :facts        '({:v 18, :e :transient, :t 32, :a :input/key-code}),
                        :rhs          '(do (clara.rules/retract! ?fact)),
                        :state-number 6,
                        :props        {:group :cleanup}})
  (let [full-rule-name (:name rule-to-explain)]
    "$facts is (a | no longer) because $matches matched the conditions of rule $rule-name"))

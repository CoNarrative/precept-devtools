(ns precept-visualizer.matching
  (:require [precept-visualizer.event-parser :as event-parser]
            [precept.spec.lang :as lang]
            [cljs.spec.alpha :as s]
            [net.cgrand.packed-printer :as packed]))


(defn mk-colors [n]
  (-> (.-chroma js/window)
    (.scale "Spectral")
    (.colors n)))


(defn most-contrast-text-color
  "Returns black or white, whichever has most contrast given a color"
  [color]
  (if (> (-> (.chroma js/window color)
           (.luminance))
        0.179)
    "#000000"
    "#dddddd"))


(def ignored-binding-keys #{:?e___sub___impl})


(defn remove-ignored-bindings [bindings]
  (into {} (remove (fn [[k _]] (ignored-binding-keys k)) bindings)))


(defn eav-conditions->colors
  "Associates valid tokens in conditions and matched fact values
  with a color."
  [conditions bindings]
  (let [tokens (event-parser/pattern-matchable-tokens conditions)
        bindings (event-parser/prettify-all-facts (remove-ignored-bindings bindings))
        tokens-and-bindings (into tokens
                              (mapcat (fn [[k v]] [(symbol (name k))
                                                   (event-parser/prettify-all-facts v)])
                                bindings))
        colors (mk-colors (count tokens-and-bindings))
        tokens->colors (zipmap tokens-and-bindings colors)]
    (reduce
      (fn [acc [variable-kw capture]]
        (if-let [color (get tokens->colors (symbol (name variable-kw)))]
          ;; We may want to check against actual matches. Analyzing bindings and constraints
          ;; only can produce a color for a variable that wasn't used in a condition or the RHS.
          ;; e.g. ?my-fact <- [x y z] where the rule contains no further reference to
          ;; ?my-fact. May choose to highlight in red to show it's unused.
          (assoc acc capture color)
          acc))
      tokens->colors
      bindings)))


(defn display-condition-value [slot colors]
  "Returns colorized markup for a value in a condition if one exists in the
  provided color map, otherwise returns uncolored markup"
  (if-let [color (get colors slot)]
    [:span
     {:style {:background-color color
              :color (most-contrast-text-color color)}}
     (str slot)]
    [:span (str slot)]))


(defn highlight-match-in-sexpr [sexpr colors]
  [:span
   "("
   (interpose " "
     (map (fn [sym] ^{:key (str sym)} [display-condition-value sym colors])
       sexpr))
   ")"])


(defn tuple-condition-highlight
  "Highlight markup for a tuple condition i.e. [[e a v]],
  not a tuple constraint [e a v]"
  [eav colors]
  [:span
   "[["
   (interpose " "
     (map (fn [slot]
            (if (list? slot)
              ^{:key slot} [highlight-match-in-sexpr slot colors]
              ^{:key slot} [display-condition-value slot colors]))
       eav))
   "]]"
   [:br]])


(defn tuple-constraint-highlight
  "Highlight for a constraint (not a condition) i.e. [e a v]. May occur inside
  a full condition (accumulator, fact-binding)"
  [eav colors]
  [:span
   "["
   (interpose " "
     (map (fn [slot]
            (if (list? slot)
              ^{:key slot} [highlight-match-in-sexpr slot colors]
              ^{:key slot} [display-condition-value slot colors]))
       eav))
   "]"])

(defn pattern-highlight-slots [fact colors]
  (let [str-fact (str fact)]
    [:span
     (first str-fact)
     (interpose " "
       (map (fn [slot]
              ^{:key slot} [display-condition-value slot colors])
         fact))
     (last str-fact)]))

(defn pattern-highlight-fact [fact colors]
  (run! cljs.pprint/pprint ["highlighting fact with colors " fact colors])
  (if-let [color (get colors fact)]
    [:span {:style {:border-bottom (str "2px solid" color) :padding-bottom 4}}
     [pattern-highlight-slots fact colors]
     [:br]]
    [:span
     [pattern-highlight-slots fact colors]
     [:br]]))


(defn fact-binding-highlight [form colors]
  (let [fact-binding (first form)
        left-arrow (str (second form))
        tuple-constraint (nth form 2)]
    [:span
     "["
     (interpose " "
       [[:span {:style {:border-bottom (str "2px solid " (get colors fact-binding))
                        :padding-bottom 2}} (str fact-binding)]
        left-arrow
        ^{:key tuple-constraint} [tuple-constraint-highlight tuple-constraint colors]])
     "]"
     [:br]]))


(defn accumulator-highlight [form colors]
  (let [result-binding (first form)
        left-arrow (str (second form))
        accum-expr (str (nth form 2))
        from ":from"
        tuple-constraint (nth form 4)]
    [:span
     "["
     (interpose " "
       [^{:key (str form)} [display-condition-value result-binding colors]
        left-arrow
        accum-expr
        from
        ^{:key tuple-constraint} [tuple-constraint-highlight tuple-constraint colors]])
     "]"
     [:br]]))


(defn pattern-highlight
  "Returns markup for a rule's conditions or facts, displaying each on a new line."
  [eavs colors]
  [:pre
   (for [eav eavs]
     (cond
       (vector? (first eav))
       ^{:key eav} [tuple-condition-highlight (first eav) colors]

       ;; TODO. We don't have a spec that test for an accumulator condition.
       ;; Testing for :from as a temporary hack
       ;(s/valid? ::lang/accum-expr eav)
       (some #{:from} eav)
       ^{:key eav} [accumulator-highlight eav colors]

       (s/valid? ::lang/variable-binding (first eav))
       ^{:key eav} [fact-binding-highlight eav colors]

       (s/valid? ::lang/ops (first eav))
       (letfn [(display-op [depth xs last?]
                 (let [next-depth-ops-count
                        (count
                          (filter (comp #(s/valid? ::lang/ops %) first)
                            (rest xs)))]
                   (if-let [has-next (> (count xs) 2)]
                      [:span
                       {:key (str xs)
                        :style {:margin-left (* depth 12)}}
                       "["
                       (str (first xs) " ")
                       [:br]
                       ;; TODO. (second eav) is only for test case.
                       ;; Should be for any [e a v] form within the
                       ;; boolean op that is not prefixed by another op
                       [:span {:style {:margin-left (* (inc depth) 12)}}
                         [tuple-constraint-highlight (second eav) colors]
                         [:br]
                         (map-indexed #(display-op (inc depth) %2
                                         (= %1 (dec next-depth-ops-count)))
                           (nthrest xs 2))]
                       "]"]
                      [:span
                       {:key (str xs)
                        :style {:margin-left (* depth 12)}}
                       "[" (str (first xs) " ")
                       [tuple-constraint-highlight (second xs) colors]
                       "]" (when-not last? [:br])])))]
           (display-op 0 eav false))

       true
       [:span {:key eav}
        "["
        (interpose " "
          (map (fn [slot]
                 (if (list? slot)
                   ^{:key (str eav slot)} [highlight-match-in-sexpr slot colors]
                   ^{:key (str eav slot)} [display-condition-value slot colors]))
            eav))
        "]"
        [:br]]))])


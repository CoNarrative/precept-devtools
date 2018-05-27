(ns precept-visualizer.matching
  (:require [precept-visualizer.event-parser :as event-parser]
            [precept.spec.lang :as lang]
            [cljs.spec.alpha :as s]
            [net.cgrand.packed-printer :as packed]
            [precept.core :as precept]))

;; should be format edn to str
(defn format-edn-str [edn]
  (binding [*print-readably* false]
    (-> edn
      (packed/pprint :width 48)
      (with-out-str)
      (clojure.string/trim-newline))))


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
        bindings (remove-ignored-bindings bindings)
        tokens-and-bindings (into tokens
                              (mapcat
                                (fn [[k v]]
                                  [(symbol (name k)) v])
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
          (let [acc2 (-> acc
                      (assoc capture color)
                       ;; If a collection assoc prettified / short uuid
                       ;; version with the color too
                      (assoc (event-parser/prettify-all-facts
                               capture
                               {:trim-uuids? true})
                             color))]
            ;; If a uuid assoc the short form with the color
            (if (uuid? capture)
              (assoc acc2 (event-parser/trim-uuid capture) color)
              acc2))
          acc))
      tokens->colors
      bindings)))


(defn display-condition-value [slot colors]
  "Returns colorized markup for a value in a condition if one exists in the
  provided color map, otherwise returns uncolored markup"
  (let [s (if (uuid? slot)
            (event-parser/trim-uuid slot)
            (format-edn-str slot))]
    (if-let [color (get colors slot)]
      [:span
       {:style {:background-color color
                :color (most-contrast-text-color color)}}
       s]
      [:span s])))


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
  (let [[first-char last-char] (-> fact (str) ((juxt first last)))]
    [:span
     first-char
     (interpose " "
       (map-indexed (fn [i slot]
              ^{:key (str i slot)} [display-condition-value slot colors])
         fact))
     last-char]))

(defn pattern-highlight-entity-map [fact colors]
  [:span "{"
   (interpose " "
     (map (fn [slot]
            ^{:key slot} [display-condition-value slot colors])
       (reduce concat fact)))
   "}"])


(defn pattern-highlight-fact [fact colors]
  (let [fact-format (:fact-format @(precept/subscribe [:settings]))
        formatted (event-parser/prettify-all-facts fact {:trim-uuids? true :format fact-format})]
    ;; If we have a color for the "whole fact" (i.e. result binding) go ahead and highlight it
    (if-let [color (get colors fact)]
      [:span {:style {:border-bottom (str "2px solid" color)
                      :padding-bottom 4}}
       (format-edn-str formatted)]
      (if (= fact-format :vector)
        [:span
         [pattern-highlight-slots
          formatted
          colors]]
        [:span
         [pattern-highlight-entity-map
          formatted
          colors]]))))


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
       [[:span {:key (str form)
                :style {:border-bottom (str "2px solid " (get colors result-binding))
                        :padding-bottom 2}}
         (str result-binding)]
        left-arrow
        accum-expr
        from
        ^{:key tuple-constraint} [tuple-constraint-highlight tuple-constraint colors]])
     "]"
     [:br]]))

(defn ops-exprs [exprs]
  (filter (comp #(s/valid? ::lang/ops %) first) exprs))

(defn display-op [depth xs last? colors]
  (let [next-depth-ops-count (->> (rest xs)
                               (ops-exprs)
                               (count))]
    (if-let [has-next (> (count xs) 2)]
      [:span
       {:key (str xs)
        :style {:margin-left (* depth 12)}}
       "["
       (str (first xs) " ")
       [:br]
       [:span {:style {:margin-left (* (inc depth) 12)}}
        [tuple-constraint-highlight (second xs) colors]
        [:br]
        (map-indexed
          #(display-op
             (inc depth)
             %2
             (= %1 (dec next-depth-ops-count))
             colors)
          (nthrest xs 2))]
       "]"]
      [:span
       {:key   (str xs)
        :style {:margin-left (* depth 12)}}
       "[" (str (first xs) " ")
       [tuple-constraint-highlight (second xs) colors]
       "]" (when-not last? [:br])])))

(defn display-boolean [depth xs last? colors]
  (if-let [op (#{:or :and} (first xs))]
    [:span
     {:key   (str xs)
      :style {:margin-left (* depth 12)}}
     "["
     (str op " ")
     [:br]
     (if (#{:or :and} (first (second xs)))
       (map-indexed (fn [i x] (display-boolean (inc depth) x (= (inc i) (count (rest xs))) colors)) (rest xs))
       (display-boolean (inc depth)
                        (rest xs)
                        nil colors))
     [:span "]"] (when-not last? [:br])]
    (map-indexed (fn [i t]
                   [:span {:key i :style {:margin-left (* 12 depth)}}
                    [tuple-constraint-highlight t colors]
                    (when (not= (inc i) (count xs))
                      [:br])])
                 xs)))

(defn pattern-highlight
  "Returns markup for a rule's conditions or facts, displaying each on a new line."
  [eavs colors]
  [:pre
   (->> eavs
        (map-indexed
          (fn [i eav]
            (cond
              (vector? (first eav))
              ^{:key (str eav i)} [tuple-condition-highlight (first eav) colors]

              ;; TODO. We don't have a spec for an accumulator condition.
              ;; Testing for :from as a temporary hack
              ;(s/valid? ::lang/accum-expr eav)
              (some #{:from} eav)
              ^{:key (str eav i)} [accumulator-highlight eav colors]

              (s/valid? ::lang/variable-binding (first eav))
              ^{:key (str eav i)} [fact-binding-highlight eav colors]

              (#{:and :or} (first eav))
              (display-boolean 0 eav false colors)

              (s/valid? ::lang/ops (first eav))
              (display-op 0 eav false colors)

              true
              [:span {:key (str eav i)}
               "["
               (interpose " "
                          (map-indexed (fn [j slot]
                                 (if (list? slot)
                                   ^{:key (str eav slot i j)} [highlight-match-in-sexpr slot colors]
                                   ^{:key (str eav slot i j)} [display-condition-value slot colors]))
                               eav))
               "]"
               [:br]]))))])

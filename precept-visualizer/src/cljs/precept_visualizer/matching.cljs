(ns precept-visualizer.matching
  (:require [precept-visualizer.event-parser :as event-parser]
            [precept.spec.lang :as lang]
            [cljs.spec.alpha :as s]))


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
    "#ffffff"))


(defn eav-conditions->colors
  "Zips ast-parsed conditions with bindings to return map of variable bindings and value matches to colors"
  [conditions bindings]
  (let [tokens (event-parser/pattern-matchable-tokens conditions)
        colors (mk-colors (count tokens))
        m (zipmap tokens colors)]
    (reduce
      (fn [acc [variable-kw capture]]
        (if-let [color (get m (symbol (name variable-kw)))]
          (assoc acc capture color)))
      m
      bindings)))


(defn display-condition-value [colors slot]
  "Returns colorized markup for a value in a condition if one exists in the
  provided color map, otherwise returns uncolored markup"
  (if-let [color (get colors slot)]
    [:span {:key slot
            :style {:background-color color
                    :color (most-contrast-text-color color)}}

     (str slot)]
    [:span {:key slot} (str slot)]))


(defn highlight-match-in-sexpr [sexpr colors]
  [:span
   "("
   (interpose " "
     (map (fn [sym] (display-condition-value colors sym))
       sexpr))
   ")"])


(defn vector-condition-highlight [eav colors]
  [:span
   "[["
   (interpose " "
     (map (fn [slot]
            (if (list? slot)
              ^{:key slot} [highlight-match-in-sexpr slot colors]
              (display-condition-value colors slot)))
       eav))
   "]]"
   [:br]])


(defn fact-binding-highlight [eav colors]
  [:span
   "["
   (interpose " "
     (map (fn [slot]
            (if (list? slot)
              ^{:key slot} [highlight-match-in-sexpr slot colors]
              (display-condition-value colors slot)))
       eav))
   "]"
   [:br]])


(defn pattern-highlight
  "Returns markup for a rule's conditions or facts, displaying each on a new line."
  [eavs colors]
  [:pre
   (for [eav eavs]
     (cond
       (vector? (first eav))
       ^{:key eav} [vector-condition-highlight (first eav) colors]

       (s/valid? ::lang/accum-expr (first eav))
       ^{:key eav} [fact-binding-highlight eav colors]

       true
       [:span {:key eav}
        "["
        (interpose " "
          (map (fn [slot]
                 (if (list? slot)
                   ^{:key slot} [highlight-match-in-sexpr slot colors]
                   (display-condition-value colors slot)))
            eav))
        "]"
        [:br]]))])


(ns precept-visualizer.views.diff
  (:require ;;[reagent.core]
            [precept-visualizer.views.consequents :as conseq]
            [precept.core :as precept]
            [precept-visualizer.util :as util]))


(defn render-diff [];added-strs removed-strs]
  (let [;added (map cljs.reader/read-string added-strs)
        ;removed (map cljs.reader/read-string removed-strs)
        added [{:e 1 :a :foo :v 2 :t 3}]
        removed [{:e 1 :a :foo :v 1 :t 4}]
        ; Reduce into m with :added, :removed, :replaced [[added removed]] and :added
        ; :removed facts not in replaced
        e-a #(juxt :e :a %)
        _ (reduce
            (fn [acc cur]
              (let [replaced
                    (->> (:removed cur)
                      (map
                        (fn [removed-fact]
                          (reduce
                            (fn [acc added-fact]
                              (if (= (e-a removed-fact) (e-a added-fact))
                                (conj acc [added-fact removed-fact])
                                acc))
                            []
                            (:added cur)))))]))
            {}
            {:added added :removed removed})]))

;[add-diff remove-diff common] (clojure.data/diff added removed)
;(if (every? #{:e :a} (keys common))
;  (mapv :v [add-diff remove-diff])
;  false))


(defn fact [fact-str]
  [:div {:on-click #(conseq/fact-explanation-requested fact-str)}
   [:pre {:style {:cursor "pointer"}}
    [:span "["
     (interpose " "
       (map (fn [x] [:span {:key (str fact-str "-" x)} (str x)])
         (util/display-eav (cljs.reader/read-string fact-str))))
     "]"]]])


(defn diff-view []
  (let [{:state/keys [added removed]} @(precept/subscribe [:diff-view])]
    [:div
     [:h1 "Diff"]
     [:h3 "Added"]
     (if (empty? added)
       [:div "None"]
       (for [fact-str added]
         ^{:key fact-str} [fact fact-str]))
     [:h3 "Removed"]
     (if (empty? removed)
       [:div "None"]
       (for [fact-str removed]
         ^{:key fact-str} [fact fact-str]))]))



(ns precept-visualizer.views.diff
  (:require
    [precept-visualizer.views.consequents :as conseq]
    [precept.core :as precept]
    [precept-visualizer.util :as util]
    [precept-visualizer.event-parser :as event-parser]
    [precept-visualizer.matching :as matching]
    [precept-visualizer.icons :as icons]
    [reagent.core :as r]))


(defn fact
  "Returns markup for a fact. Triggers an explanation of the fact instance via
  the e a v t fact string on click."
  [{:keys [fact-str eavt-map formatted-edn theme tracking-identity? viewing-occurrence?] :as props}]
  (r/with-let [hovered? (r/atom false)]
    [:div {:on-mouse-enter #(reset! hovered? true)
           :on-mouse-leave #(reset! hovered? false)
           :on-click #(conseq/fact-explanation-requested eavt-map)}
     [:div {:title "Inspect"
            :style {:display "flex" :justify-content "flex-end" :cursor "pointer"}}
      [icons/eye {:style {:margin  6
                          :opacity (if (or @hovered? tracking-identity? viewing-occurrence?) 1 0)
                          :color   (cond
                                     viewing-occurrence? (:accent-text-color theme)
                                     tracking-identity? (:text-color theme)
                                     :else (:disabled-text-color theme))}}]]
     [:pre {:style {:cursor "pointer"}}
      (matching/format-edn-str formatted-edn)]]))

(defn facts [{:keys [fact-strs formatted-edn eavt-maps tracked-e-as viewing-fact-ts theme] :as props}]
  [:div
   (for [[fact-str fact-edn fact-edn-map] (map vector fact-strs formatted-edn eavt-maps)]
     ^{:key fact-str} [fact {:formatted-edn       fact-edn
                             :fact-str            fact-str
                             :eavt-map            fact-edn-map
                             :tracking-identity?  (tracked-e-as ((juxt :e :a) fact-edn-map))
                             :viewing-occurrence? (viewing-fact-ts (:t fact-edn-map))
                             :theme               theme}])])

(defn op-strs->edn [strs edn-format-options]
  (->> strs
   (mapv cljs.reader/read-string)
   (mapv #(event-parser/prettify-all-facts % edn-format-options))))

(defn diff-view [theme schemas]
  (let [{:state/keys [added removed]} @(precept/subscribe [:diff-view])
        {:keys [tracked-e-as viewing-fact-ts]} @(precept/subscribe [:fact-tracker-status])
        added-edn-maps (->> added (mapv cljs.reader/read-string))
        removed-edn-maps (->> removed (mapv cljs.reader/read-string))
        fact-format (:fact-format @(precept/subscribe [:settings]))
        edn-format-options {:trim-uuids? true :format fact-format :one-to-many (:one-to-manies schemas)}
        added-edn (mapv #(event-parser/prettify-all-facts % edn-format-options) added-edn-maps)
        removed-edn (mapv #(event-parser/prettify-all-facts % edn-format-options) removed-edn-maps)]
    [:div
     [:h3 {:style {:color (:text-color theme)}}
      "Added"]
     (if (empty? added)
       [:div "None"]
       [facts {:fact-strs       added
               :formatted-edn   added-edn
               :eavt-maps       added-edn-maps
               :tracked-e-as    tracked-e-as
               :viewing-fact-ts viewing-fact-ts
               :theme           theme}])
     [:h3 {:style {:color (:text-color theme)}}
      "Removed"]
     (if (empty? removed)
       [:div {:style {:color (:text-color theme)}}
        "None"]
       [facts {:fact-strs       removed
               :formatted-edn   removed-edn
               :eavt-maps       removed-edn-maps
               :tracked-e-as    tracked-e-as
               :viewing-fact-ts viewing-fact-ts
               :theme           theme}])]))


(ns precept-visualizer.views.diff
  (:require
    [precept-visualizer.views.consequents :as conseq]
    [precept.core :as precept]
    [precept-visualizer.util :as util]
    [precept-visualizer.event-parser :as event-parser]
    [precept-visualizer.matching :as matching]))



(defn fact
  "Returns markup for a fact. Triggers an explanation of the fact instance via
  the e a v t fact string on click."
  [fact-edn fact-str fact-edn-map]
  [:div {:on-click #(conseq/fact-explanation-requested fact-edn-map)}
   [:pre {:style {:cursor "pointer"}}
     (matching/format-edn-str fact-edn)]])

(defn op-strs->edn [strs edn-format-options]
  (->> strs
   (mapv cljs.reader/read-string)
   (mapv #(event-parser/prettify-all-facts % edn-format-options))))

(defn diff-view [theme schemas]
  (let [{:state/keys [added removed]} @(precept/subscribe [:diff-view])
        added-edn-maps (->> added (mapv cljs.reader/read-string))
        removed-edn-maps (->> removed (mapv cljs.reader/read-string))
        fact-format (:fact-format @(precept/subscribe [:settings]))
        edn-format-options {:trim-uuids? true :format fact-format}
        added-edn (mapv #(event-parser/prettify-all-facts % edn-format-options) added-edn-maps)
        removed-edn (mapv #(event-parser/prettify-all-facts % edn-format-options) removed-edn-maps)]
    [:div
     [:h3 {:style {:color (:text-color theme)}}
      "Added"]
     (if (empty? added)
       [:div "None"]
       (for [[fact-str fact-edn fact-edn-map] (map vector added added-edn added-edn-maps)]
         ^{:key fact-str} [fact fact-edn fact-str fact-edn-map]))
     [:h3 {:style {:color (:text-color theme)}}
      "Removed"]
     (if (empty? removed)
       [:div {:style {:color (:text-color theme)}}
        "None"]
       (for [[fact-str fact-edn fact-edn-map] (map vector removed removed-edn removed-edn-maps)]
         ^{:key fact-str} [fact fact-edn fact-str fact-edn-map]))]))

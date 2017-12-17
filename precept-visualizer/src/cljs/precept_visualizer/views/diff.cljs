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
  [fact-edn fact-str]
  [:div {:on-click #(conseq/fact-explanation-requested fact-str)}
   [:pre {:style {:cursor "pointer"}}
     (matching/format-edn-str fact-edn)]])

(defn op-strs->edn [strs edn-format-options]
  (->> strs
   (mapv cljs.reader/read-string)
   (mapv #(event-parser/prettify-all-facts % edn-format-options))))

(defn diff-view [theme schemas]
  (let [{:state/keys [added removed]} @(precept/subscribe [:diff-view])
        fact-format (:fact-format @(precept/subscribe [:settings]))
        edn-format-options {:trim-uuids? true :format fact-format}
        added-edn (op-strs->edn added edn-format-options)
        removed-edn (op-strs->edn removed edn-format-options)]
    [:div
     [:h3 {:style {:color (:text-color theme)}}
      "Added"]
     (if (empty? added)
       [:div "None"]
       (for [[fact-str fact-edn] (map vector added added-edn)]
         ^{:key fact-str} [fact fact-edn fact-str]))
     [:h3 {:style {:color (:text-color theme)}}
      "Removed"]
     (if (empty? removed)
       [:div {:style {:color (:text-color theme)}}
        "None"]
       (for [[fact-str fact-edn] (map vector removed removed-edn)]
         ^{:key fact-str} [fact fact-edn fact-str]))]))

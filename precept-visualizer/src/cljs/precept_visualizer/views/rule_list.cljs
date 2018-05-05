(ns precept-visualizer.views.rule-list
  (:require [precept-visualizer.views.consequents :as conseq]
            [precept-visualizer.views.explanations :as explanations]
            [precept.core :as precept]
            [precept-visualizer.icons :as icons]))

(defn prettify-rule [s]
  (let [[left right] (-> s (clojure.string/split #"=>") #_(clojure.string/split #":-"))]
    (-> left
        (clojure.string/trim)
        (clojure.string/replace #" \[" "\n[")
        (vector)
        (concat ["=>" (clojure.string/trim right)])
        (->> (clojure.string/join \newline)))))


(defn rule-item [{:keys [name type source] :as rule}
                 {:keys [log-entry selected-event-index total-event-count] :as history}
                 theme]
  [:div
   [:div {:style {:display "flex" :justify-content "flex-end"}}
    [:div
     {:on-click #(conseq/viewing-rule-history (str name) (not (boolean log-entry)))} ;; TODO. stringify before here
     [:div {:style {:height 24 :cursor "pointer"}
            :title "History"}
      [icons/clock {:style {:margin 6}}]
      (if history "Hide history " "")]]]
   [:strong name]
   [:div type]
   [:pre (if (#{"rule" "subscription"} type)
           (prettify-rule (str source))
           (str source))]
   (cond
     (nil? log-entry)
     [:div ""]
     (empty? log-entry)
     [:div "No history"]
     :else
     [explanations/rule-tracker name history theme])])


(defn key-by [f coll]
  (reduce
    (fn [acc m]
      (assoc acc (f m) m))
    {}
    coll))

(defn rule-list [rules theme]
  (let [rule-history (key-by :name (:subs @(precept/subscribe [:rule-history])))]
    [:div {:style {:display "flex" :flex-direction "column"}}
      (for [rule @rules]
        (let [history (get rule-history (str (:name rule)))]
          ^{:key (:name rule)} [rule-item rule history theme]))]))

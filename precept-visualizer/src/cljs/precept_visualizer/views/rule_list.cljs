(ns precept-visualizer.views.rule-list
  (:require [precept-visualizer.util :as util]
            [precept-visualizer.views.consequents :as conseq]
            [net.cgrand.packed-printer :as packed]
            [precept-visualizer.views.explanations :as explanations]
            [precept.core :as precept]))

(defn prettify-rule [s]
  (let [[left right] (-> s (clojure.string/split #"=>") #_(clojure.string/split #":-"))]
    (-> left
        (clojure.string/trim)
        (clojure.string/replace #" \[" "\n[")
        (vector)
        (concat ["=>" (clojure.string/trim right)])
        (->> (clojure.string/join \newline)))))


(defn rule-item [{:keys [name type source] :as rule} history theme]
  (let [_ (println "rule item history" history)]
    [:div
     [:div {:style {:display "flex" :justify-content "flex-end"}}
      [:div
       {:on-click #(conseq/viewing-rule-history (str name) (not (boolean history)))} ;; TODO. stringify before here
       (if history "Hide history" "Show history")]]
     [:strong name]
     [:div type]
     [:pre (if (#{"rule" "subscription"} type)
             (prettify-rule (str source))
             (str source))]
     (when history
       [explanations/explanation {:event history} theme])]))



(defn rule-list [rules theme]
  (let [rule-history @(precept/subscribe [:rule-history])
        _ (println "rule sub" rule-history)]
    [:div {:style {:display "flex" :flex-direction "column"}}
      (for [rule @rules]
        (let [history (when (= (str (:name rule))
                               (:name rule-history))
                        (:log-entry rule-history))]
          ^{:key (:name rule)} [rule-item rule history theme]))]))

;(packed/pprint
;  (prettify-rule
;    "(rule less-than-500 [:and [?e :random-number ?v] [:not [?e :greater-than-500]] [:not [:global :random-number ?v]]] => (insert! [?e :less-than-500 true]))"))

;(println (prettify-rule "(rule entities-with-greater-than-500 [[?e :greater-than-500]] [(<- ?fact (entity ?e))] => (insert! [:report :entity>500 ?fact]))"))

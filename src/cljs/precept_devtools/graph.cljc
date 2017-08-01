(ns precept-devtools.graph)



(defn fact-node [fact-id parent-eid tuple]
  {:data (merge {:id fact-id :parent parent-eid} tuple)})

(defn entity-container-node [eid]
 {:data (merge {:id eid})})

(defn edge [id source target]
  {:data {:id id
          :source source
          :target target}})

(defn get-el [id] (.$ js/cytoscape (str "#" id)))

(defn make-label* [el]
  (let [data (.data el)]
    (if (get data "parent")
      (get data "e")
      (str
        (get data "a")
        " "
        (.stringify js/JSON (get data "v"))))))

(def make-label (memoize make-label*))

(defn update-batch
  [fs]
  "Calls sequence of cytoscape functions that update the existing graph.
  http://js.cytoscape.org/#cy.batch "
  (.-startBatch js/cytoscape)
  (doseq [f fs] (f))
  (.-endBatch js/cytoscape))

; Supposing Tuples keyed by :e
(defn create-elements
  [xs]
  (mapcat
    (fn [[e tuples]]
      (into [(entity-container-node (str e))]
        (map
          (fn [{:keys [e a v t]} tuple]
            (fact-node (str t) (str e) tuple))
          tuples)))
    xs))

(def body (aget (.getElementsByTagName js/document "body") 0))

(let [div (.createElement js/document "div")
      _ (set! (.-id div) "graph")
      _ (set! (-> div .-style .-width) "800px")
      _ (set! (-> div .-style .-height) "600px")]
  (.appendChild body div))

(defn init! [e-tuples]
  (js/cytoscape
    (clj->js
      {:container (.getElementById js/document "graph")
       :elements  [{:data {:id "a"}}
                   {:data {:id "b" :first-name "foo"}}
                   {:data {:id "c" :parent "b"}}
                   {:data {:id "ab" :source "a" :target "b"}}]
       :style     [{:selector "node"
                    :style    {"background-color" "#666"
                               "label"            (fn [el]
                                                    (println "Data" (.stringify js/JSON (.data el)))
                                                    (.stringify js/JSON (.data el)))}}
                   {:selector "edge"
                    :style    {"width"              3
                               "line-color"         "#ccc"
                               "target-arrow-color" "#ccc"
                               "target-arrow-shape" "triangle"}}]
       :layout    {:name "cose"}})))


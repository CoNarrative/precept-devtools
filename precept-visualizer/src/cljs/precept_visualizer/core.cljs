(ns precept-visualizer.core
  (:require [reagent.core :as r]
            [precept.core :as precept]
            [precept-visualizer.util :as util]
            [precept-visualizer.views.core :as views]
            [mount.core :as mount]
            [precept-visualizer.rules.core :refer [visualizer-session]]
            [precept-visualizer.state :as viz-state]))


(defn render!
  ([] (render! {:rules viz-state/rule-definitions
                :orm-states viz-state/orm-ratom}))
  ([{:keys [rules orm-states]}]
   (let [mount-node-id "precept-devtools"
         mount-node (util/get-or-create-mount-node! mount-node-id)]
     (r/render [views/main {:rules rules :store orm-states}]
               mount-node))))

(defn ^:export main []
  (mount/start)
  (precept/start! {:session visualizer-session
                   :facts [[:transient :start true]]})
  (render!))


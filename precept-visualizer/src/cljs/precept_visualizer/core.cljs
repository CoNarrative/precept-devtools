(ns precept-visualizer.core
  (:require [precept.state :as state]
            [reagent.core :as r]
            [precept.core :as core]
            [precept-visualizer.util :as util]
            [precept-visualizer.views :as views]
            [mount.core :as mount]
            [precept-visualizer.rules :refer [visualizer-session]]
            [precept-visualizer.ws :as ws]
            [precept-visualizer.state :as viz-state]))


(defn render!
  ([] (render! {:rules precept.state/rules :orm-states viz-state/orm-ratom}))
  ([{:keys [rules orm-states]}]
   (let [mount-node-id "precept-devtools"
         mount-node (util/get-or-create-mount-node! mount-node-id)]
     (r/render [views/main-container {:rules rules :store orm-states}]
               mount-node))))

(defn main []
  (mount/start)
  (core/start! {:session visualizer-session
                :facts [[:transient :start true]]})
  ;(ws/get-log) ;; TODO. Socket not open when this runs
  (render!))


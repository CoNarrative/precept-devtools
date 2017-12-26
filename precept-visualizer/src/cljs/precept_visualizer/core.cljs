(ns precept-visualizer.core
  (:require [precept.state :as state]
            [reagent.core :as r]
            [precept.core :as precept]
            [precept-visualizer.util :as util]
            [precept-visualizer.views.core :as views]
            [mount.core :as mount]
            [devtools.core :as binaryage-devtools]
            [precept-visualizer.rules-core :refer [visualizer-session]]
            [precept-visualizer.ws :as ws]
            [precept-visualizer.state :as viz-state]
            [precept-visualizer.mouse :as mouse]))

(binaryage-devtools/install!)



(defn render!
  ;; TODO. Render rules from target session, not devtools session
  ([] (render! {:rules viz-state/rule-definitions
                :orm-states viz-state/orm-ratom}))
  ([{:keys [rules orm-states]}]
   (let [mount-node-id "precept-devtools"
         mount-node (util/get-or-create-mount-node! mount-node-id)]
     (r/render [views/main {:rules rules :store orm-states}]
               mount-node))))

(defn main []
  (mount/start)
  (precept/start! {:session visualizer-session
                   :facts [[:transient :start true]]})
  ;(mouse/add-listeners! {:click (fn [e] (.log js/console "e" e))} {})
  (render!))


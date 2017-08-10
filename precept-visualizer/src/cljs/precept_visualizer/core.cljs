(ns precept-visualizer.core
  (:require [precept.state :as state]
            [reagent.core :as r]
            [precept-visualizer.util :as util]
            [precept-visualizer.views :as views]))


(defn render!
  ([] (render! {:rules precept.state/rules :store precept.state/store}))
  ([{:keys [rules store] :as precept-state}]
   (let [mount-node-id "precept-devtools"
         mount-node (util/get-or-create-mount-node! mount-node-id)]
     (r/render [views/main-container {:rules rules :store store}]
               mount-node))))

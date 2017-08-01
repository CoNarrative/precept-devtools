(ns precept-devtools.routes.api
    (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
              ;[precept-devtools.db.core :refer [db]]
              [precept-devtools.routes.ws :as ws]))

(defn update! [m]
  (ws/chsk-send! (:user-id m) (:data m)))

(defroutes api-routes
  (POST "/update" [m] (update! m)))

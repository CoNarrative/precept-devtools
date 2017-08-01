(ns precept-devtools.handler
    (:require [compojure.core :refer [routes wrap-routes]]
              [precept-devtools.routes.home :refer [home-routes]]
              [precept-devtools.routes.ws :refer [ws-routes]]
              [compojure.route :as route]
              [precept-devtools.env :refer [defaults]]
              [mount.core :as mount]
              [precept-devtools.routes.api :refer [api-routes]]
              [precept-devtools.middleware :as middleware]))

(mount/defstate init-app
  :start ((or (:init defaults) identity))
  :stop  ((or (:stop defaults) identity)))

(def app-routes
  (routes
    #'ws-routes
    (-> #'api-routes (wrap-routes middleware/wrap-formats))
    (-> #'home-routes (wrap-routes middleware/wrap-formats))
    (route/not-found {:status 404})))

(defn app [] (middleware/wrap-base #'app-routes))

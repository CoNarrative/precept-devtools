(ns precept-devtools.routes.home
  (:require [hiccup.page :refer [html5 include-js]]
            [compojure.core :refer [defroutes GET]]
            [compojure.route :as route]
            [ring.util.response :as response]))


(defroutes home-routes
  (route/resources "/")
  (GET "*" []
    (-> (response/resource-response "index.html" {:root "public"})
        (response/content-type "text/html")) ))

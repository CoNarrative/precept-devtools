(ns precept-devtools.routes.home
    (:require [hiccup.page :refer [html5 include-js]]
              [compojure.core :refer [defroutes GET]]))

(defn home-page []
  (html5
    [:head]
    [:div#app]
    [:div#graph]
    (include-js "js/app.js")))

(defroutes home-routes
  (GET "*" [] (home-page)))

(ns precept-devtools.routes.api
    (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
              [precept-devtools.db :refer [db]]
              [precept-devtools.routes.ws :as ws]))

(defn update! [m]
  (ws/chsk-send! (:user-id m) (:data m)))

(defn get-log-states-in-range [n1 n2]
  (let [log (:log @db)
        requested-range (apply range [n1 (inc n2)])]
    (mapv #(get log %) requested-range)))

(defroutes api-routes
  (POST "/update" [m] (update! m))
  (GET "/log/range/:n1/:n2" [n1 n2]
    (str ;; TODO. transit
      (get-log-states-in-range (Integer/parseInt n1)
                               (Integer/parseInt n2)))))


(ns precept-devtools.routes.api
    (:require [compojure.core :refer [defroutes GET POST PUT DELETE]]
              [precept-devtools.db :refer [db]]
              [precept-devtools.routes.ws :as ws]))

(defn update! [m]
  (ws/chsk-send! (:user-id m) (:data m)))

(defn get-log-states-in-range [n1 n2]
  (let [log (:log @db)
        num-states-available (count log)]
    (if (or (zero? num-states-available)
            (> n1 num-states-available))
        []
        (if (> n2 num-states-available)
          (-> log
              (subvec n1 num-states-available)
              (conj (repeat (- n2 num-states-available) [])))
          (subvec log n1 n2)))))

(defroutes api-routes
  (POST "/update" [m] (update! m))
  (GET "/log/range/:n1/:n2" [n1 n2]
    (str ;; TODO. transit
      (get-log-states-in-range (Integer/parseInt n1)
                               (Integer/parseInt n2)))))

(comment
  (get-log-states-in-range 1 3)
  (subvec (:log @db) 0 3)
  (subvec [1 2 3] 0 3))

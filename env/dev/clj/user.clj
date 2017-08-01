(ns user
  (:require [mount.core :as mount]
            [precept-devtools.figwheel :refer [start-fw stop-fw cljs]]
            [precept-devtools.core]))

(defn start []
  (mount/start-without #'precept-devtools.core/http-server
                       #'precept-devtools.core/repl-server))

(defn stop []
  (mount/stop-except #'precept-devtools.core/http-server
                     #'precept-devtools.core/repl-server))

(defn restart []
  (stop)
  (start))



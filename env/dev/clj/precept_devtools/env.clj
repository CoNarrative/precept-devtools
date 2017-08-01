(ns precept-devtools.env
  (:require [clojure.tools.logging :as log]
            [precept-devtools.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[precept-fullstack started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[precept-fullstack has shut down successfully]=-"))
   :middleware wrap-dev})

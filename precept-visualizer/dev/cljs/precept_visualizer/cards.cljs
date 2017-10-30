(ns ^:figwheel-no-load precept-visualizer.cards
  (:require [reagent.core :as r]
            [devcards.core :as devcards]
            [precept-visualizer.core :as core]
            [precept-visualizer.devcards.core :as cards]
            [figwheel.client :as figwheel :include-macros true])
  (:require-macros [devcards.core :refer [start-devcard-ui!]]))


(enable-console-print!)


(figwheel/watch-and-reload
  :load-warninged-code true)
  ;:on-jsload #(do (println "Loaded.")))

(start-devcard-ui!)
;(cards/render)
;(require 'precept-visualizer.devcards.core)

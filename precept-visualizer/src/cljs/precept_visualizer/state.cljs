(ns precept-visualizer.state
  (:require [reagent.core :as r]))

(def orm-ratom (r/atom []))
(def rule-definitions (r/atom {}))
(def event-log (r/atom []))

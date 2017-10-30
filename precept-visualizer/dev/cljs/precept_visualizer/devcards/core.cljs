(ns precept-visualizer.devcards.core
  (:require [devcards.core :as dc]
            [precept-visualizer.views :as views]
            [reagent.core :as r]
            [clojure.spec.alpha :as s])
  (:require-macros [devcards.core :refer [defcard defcard-rg reagent]]))

(def explanation-no-action-data
  {:fact-str (str {:e :global, :a :fact, :v "falsee", :t 9})
   :event
   {:bindings {:?e :global},
    :name "precept.app-ns/define-912335779",
    :type :add-facts-logical,
    :ns-name 'precept.app-ns,
    :lhs [{:constraints ['(= ?e (:e this))], :type :foo}]
    :event-number 3,
    :matches [[{:v 808, :e :global, :t 8, :a :foo} 18]],
    :id #uuid "5567f008-d261-48d9-8ccf-c6c6a0821af7",
    :display-name "[{:type :foo, :constraints [(= ?e (:e this))]}]",
    :state-id #uuid "c956384f-0660-4785-860f-66ef024cc1a3",
    :facts '({:v "falsee", :e :global, :t 9, :a :fact}),
    :rhs '(do (precept.util/insert! [?e :fact "falsee"])),
    :state-number 4,
    :props nil}})

(defcard-rg explanation-no-action
  (fn [a _] [views/explanation @a])
  explanation-no-action-data
  {:inspect-data true :history false :watch-atom true})

(defn render [])


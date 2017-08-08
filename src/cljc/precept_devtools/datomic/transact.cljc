(ns precept-devtools.datomic.transact
  (:require [precept-devtools.transactions :as txs]))

(defn persist-state! [state]
  (let [{:keys [state-id state-number]} state
        *temp-id (atom 0)
        next-temp-id! (fn [] (reset! *temp-id (dec @*temp-id)))
        state-tx (txs/create-state-tx (next-temp-id!) state-id state-number)]))

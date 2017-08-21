(ns precept-devtools.db)


(def db (atom {:states []
               :log []
               :orm-states []
               :schemas nil
               :ancestors-fn nil}))
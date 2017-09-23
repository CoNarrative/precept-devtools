(ns precept-devtools.db)


(def initial-state {:states          []
                    :log             []
                    :orm-states      []
                    :visualizer-uids []
                    :schemas         nil
                    :ancestors-fn    nil})

(def db (atom initial-state))
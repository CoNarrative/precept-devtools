(defproject precept-visualizer "0.0.0"
  :description "Precept visualizer"
  :url          "https://github.com/CoNarrative/precept.git"
  :license      {:name "MIT"
                 :url "https://github.com/CoNarrative/precept/blob/master/LICENSE"}
  :dependencies [[cljsjs/cytoscape "3.1.4-0"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 [com.taoensso/sente "1.11.0"]
                 [org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/spec.alpha "0.1.109"]
                 [binaryage/devtools "0.8.2"]
                 [org.clojure/clojurescript "1.9.671"]
                 [org.clojure/core.async "0.3.442"]
                 [mount "0.1.11"]
                 [net.cgrand/packed-printer "0.2.1"]
                 [precept "0.4.0-alpha"]
                 [reagent "0.6.0"]]

  :plugins [[lein-cljsbuild "1.1.4"]]

  :main precept-visualizer.core

  :source-paths ["src/cljs" "src/cljc"]

  :test-paths ["test/cljs"]

  :resource-paths ["resources" "target/cljsbuild"]

  :figwheel
  {:http-server-root "public"
   :server-port 3450
   :nrepl-port 7003
   :css-dirs ["resources/public/css"]
   :reload-clj-files {:clj true :cljc true}
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles
  {:dev
   {:dependencies [[com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                   [figwheel-sidecar "0.5.11"]
                   [devcards "0.2.4"]]

    :plugins      [[lein-figwheel "0.5.11"]]

    :repl-options {:init-ns user}
    :source-paths ["src/cljs" "src/cljc"]
    :resource-paths ["env/dev/resources"]

    :cljsbuild
    {:builds
     [{:id "dev"
       :source-paths ["dev/clj" "dev/cljs" "src/cljs" "src/cljc"]
       :compiler
                    {:main "precept-visualizer.app"
                     :output-to "target/cljsbuild/public/js/app.js"
                     :output-dir "target/cljsbuild/public/js/out"
                     :asset-path "/js/out"
                     :optimizations :none
                     :cache-analysis false
                     :source-map true
                     :pretty-print true}}
      {:id "devcards"
       :source-paths ["dev/clj" "dev/cljs" "src/cljs" "src/cljc"]
       :figwheel { :devcards true}
       :compiler { :main    "precept-visualizer.cards"
                   :asset-path "js/devcards_out"
                   :optimizations :none
                   :source-map true
                   :output-to  "target/cljsbuild/public/js/visualizer_devcards.js"
                   :output-dir "target/cljsbuild/public/js/devcards_out"
                   :source-map-timestamp true}}]}
    :deploy-repositories [["releases"  {:sign-releases false
                                        :url "https://clojars.org/repo"}]
                          ["snapshots" {:sign-releases false
                                        :url "https://clojars.org/repo"}]]}})

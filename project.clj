(defproject precept-devtools "0.0.0"
  :description "Precept dev tools"
  :url          "https://github.com/CoNarrative/precept.git"
  :license      {:name "MIT"
                 :url "https://github.com/CoNarrative/precept/blob/master/LICENSE"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/spec.alpha "0.1.109"]
                 [org.clojure/clojurescript "1.9.562"]
                 [compojure "1.5.2"]
                 [cprop "0.1.10"]
                 [hiccup "1.0.5"]
                 [luminus-http-kit "0.1.4"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.1"]
                 [metosin/compojure-api "1.1.10" :exclusions [prismatic/schema]]
                 [metosin/ring-http-response "0.8.1"]
                 [mount "0.1.11"]
                 [prismatic/schema "1.1.6"]
                 [org.clojure/clojurescript "1.9.671"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [precept "0.4.0-alpha"]
                 [reagent "0.6.0"]
                 [reagent-utils "0.2.0"]
                 [ring-middleware-format "0.7.2"]
                 [ring/ring-core "1.5.1"]
                 [ring/ring-defaults "0.2.3"]
                 [secretary "1.2.3"]
                 [selmer "1.10.6"]
                 [com.taoensso/sente "1.11.0"]
                 [precept "0.4.0-alpha"]
                 [cljsjs/cytoscape "3.1.4-0"]
                 [reagent "0.6.0"]]

  :plugins [[lein-cljsbuild "1.1.4"]]

  :jvm-opts ["-server" "-Dconf=.lein-env"]

  :main precept-devtools.core

  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :test-paths ["test/clj" "test/cljc"]

  :resource-paths ["resources" "target/cljsbuild"]

  :figwheel
  {:http-server-root "public"
   :nrepl-port 7002
   :reload-clj-files {:clj true :cljc true}
   :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

  :profiles
  {:dev
   {:dependencies [[com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                   [figwheel-sidecar "0.5.11"]]

    :plugins      [[lein-figwheel "0.5.11"]]

    :repl-options {:init-ns user}

    :source-paths ["dev/clj"]

    :cljsbuild
    {:builds
     {:dev
       {:source-paths ["dev" "src/cljs" "src/cljc"]
        :compiler
                     {:main "precept-devtools.app"
                      :output-to "target/cljsbuild/public/js/app.js"
                      :output-dir "target/cljsbuild/public/js/out"
                      :asset-path "/js/out"
                      :optimizations :none
                      :cache-analysis false
                      :source-map true
                      :pretty-print true}}}}

    :deploy-repositories [["releases"  {:sign-releases false
                                        :url "https://clojars.org/repo"}]
                          ["snapshots" {:sign-releases false
                                        :url "https://clojars.org/repo"}]]}})

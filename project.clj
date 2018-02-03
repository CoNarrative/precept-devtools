(defproject precept-devtools "0.1.0"
  :description "precept-devtools"
  :url          "https://github.com/CoNarrative/precept.git"
  :license      {:name "MIT"
                 :url "https://github.com/CoNarrative/precept/blob/master/LICENSE"}
  :dependencies [[compojure "1.5.2"]
                 [cprop "0.1.10"]
                 [com.cognitect/transit-clj "0.8.288" :exclusions [com.fasterxml.jackson.core]]
                 [com.fasterxml.jackson.core/jackson-core "2.8.6"]
                 [com.taoensso/sente "1.11.0"]
                 [hiccup "1.0.5"]
                 [luminus-http-kit "0.1.4"]
                 [luminus-nrepl "0.1.4"]
                 [luminus/ring-ttl-session "0.3.1"]
                 [metosin/compojure-api "1.1.10" :exclusions [prismatic/schema]]
                 [metosin/ring-http-response "0.8.1"]
                 [mount "0.1.11"]
                 [precept "0.5.0-alpha"]
                 [prismatic/schema "1.1.6"]
                 [org.clojure/clojure "1.9.0-alpha17"]
                 [org.clojure/clojurescript "1.9.671"]
                 [org.clojure/core.async "0.3.442"]
                 [org.clojure/spec.alpha "0.1.109"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [reagent "0.6.0"]
                 [reagent-utils "0.2.0"]
                 [ring-middleware-format "0.7.2"]
                 [ring/ring-core "1.5.1"]
                 [ring/ring-defaults "0.2.3"]
                 [secretary "1.2.3"]
                 [selmer "1.10.6"]]

  :jvm-opts ["-server" "-Dconf=.lein-env"]

  :main precept-devtools.core

  :source-paths ["src/clj" "src/cljc"]

  :test-paths ["test/clj" "test/cljc"]

  :resource-paths ["resources"]

  :profiles
  {:dev
   {:dependencies [[com.cemerick/piggieback "0.2.2-SNAPSHOT"]
                   [prone "1.1.4"]
                   [ring/ring-devel "1.5.1"]]

    :repl-options {:init-ns user}
    :source-paths ["env/dev/clj"]
    :resource-paths ["env/dev/resources"]

    :deploy-repositories [["releases"  {:sign-releases false
                                        :url "https://clojars.org/repo"}]
                          ["snapshots" {:sign-releases false
                                        :url "https://clojars.org/repo"}]]}})

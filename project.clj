(defproject io.kanopi/kanopi "0.1.0-SNAPSHOT"
  :description "A tool for exploring, expressing and documenting your thoughts."
  :url "http://kanopi.io"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.5.0"
  :dependencies [[org.clojure/clojure "1.9.0-alpha12"]
                 [com.cognitect/transit-clj "0.8.288"]
                 [org.clojure/core.async "0.2.391"]
                 [com.cognitect/transit-cljs "0.8.239"]
                 ;; resolves a dependency issue with figwheel and
                 ;; core.async
                 [org.clojure/core.memoize "0.5.8"]

                 [com.stuartsierra/component "0.3.1"]
                 [com.taoensso/timbre "4.7.4"]
                 [environ "1.1.0"]

                 ;; Fuzzy string matching
                 [clj-fuzzy "0.3.1"]

                 ;; Client
                 [org.clojure/clojurescript "1.9.229"]
                 [org.omcljs/om "1.0.0-alpha14"]
                 [bidi "1.22.1"]
                 [kibu/pushy "0.3.6"]
                 [com.andrewmcveigh/cljs-time "0.3.14"]
                 [com.lucasbradstreet/cljs-uuid-utils "1.0.2"]
                 [com.cemerick/url "0.1.1"]
                 [sablono "0.4.0"]
                 [cljsjs/codemirror "5.8.0-0"]

                 ; Added http stuff to ensure we're using the right
                 ; versions for cljs-ajax. It's not pretty.
                 [cljs-ajax "0.5.1"]
                 [org.apache.httpcomponents/httpasyncclient "4.1"]
                 [org.apache.httpcomponents/httpcore "4.4.3"]


                 ; Dev
                 [devcards "0.2.1-2"]

                 ;; Database
                 ; NOTE: add AWS DDB sdk when upgrading datomic
                 ; http://docs.datomic.com/storage.html#provisioning-dynamo
                 [com.datomic/datomic-pro "0.9.5327"
                  :exclusions [joda-time]]
                 [alandipert/enduro "1.2.0"]

                 ;; Web App
                 [org.immutant/web "2.1.1"]
                 [compojure "1.4.0"]
                 [liberator "0.13"]
                 ;; NOTE: this is installed in my local maven
                 ;; manually.
                 [com.cemerick/friend "0.2.1"]
                 [ring/ring-defaults "0.1.5"]
                 [ring-middleware-format "0.7.0"]
                 [hiccup "1.0.5"]
                 [crypto-password "0.1.3"]
                 [cheshire "5.5.0"] ;;; JSON
                 [clj-time "0.11.0"]
                 [garden "1.3.0-SNAPSHOT"]

                 [ring/ring-devel "1.4.0"]

                 ;; Specification and Testing
                 [prismatic/schema "1.0.3"]
                 [org.clojure/test.check "0.9.0"]]

  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username [:env/kanopi_datomic_username]
                                   :password [:env/kanopi_datomic_password]}}
  :source-paths ["src-cljc" "src"]
  :test-paths ["src-cljc" "test-cljc" "src" "test"]
  :main kanopi.main

  :plugins [[lein-environ "1.0.1"]
            [lein-marginalia "0.8.0"
             :exclusions [org.clojure/clojurescript org.clojure/clojure]]
            [lein-cljsbuild "1.1.1"]
            [lein-figwheel "0.5.0-1"]]

  :clean-targets ^{:protect false} [:target-path "resources/public/js/*.js"]

  ; https://github.com/ruedigergad/test2junit
  :test2junit-output-dir ~(or (System/getenv "CIRCLE_TEST_REPORTS") "target/test2junit")

  :aliases {"build!" ["do" "clean"
                      ["with-profile" "prod" "cljsbuild" "once"]
                      ["with-profile" "prod" "uberjar"]]}

  :profiles {:prod
             {:jvm-opts ["-XX:MaxPermSize=128M"]
              :env {:dev false}}

             :uberjar
             {:uberjar-name "kanopi-uberjar.jar"
              :aot :all
              ; this is done as part of build! alias. otherwise
              ; uberjar calls 'clean' which wipes out the compiled js
              ; resources
              :auto-clean false}

             :dev
             {:jvm-opts ["-XX:MaxPermSize=128M"]
              :plugins [
                        [test2junit "1.1.3"]
                        [lein-ancient "0.6.6"
                         :exclusions [org.clojure/tools.reader]]]
              :dependencies [[org.clojure/tools.nrepl "0.2.12"]
                             [org.clojure/tools.namespace "0.2.11"]
                             [ring/ring-devel "1.4.0"]
                             [ring/ring-mock "0.3.0"]
                             [org.clojure/data.codec "0.1.0"]
                             [org.clojure/data.csv "0.1.3"]
                             ]
              :env {:dev true}
              :source-paths ["dev"]
              :repl-options {:init-ns user}}

             :devcards
             {:plugins [[lein-environ "1.0.1"]]
              :dependencies [[org.clojure/tools.nrepl "0.2.12"]
                             [org.clojure/tools.namespace "0.2.11"]
                             [ring/ring-devel "1.4.0"]
                             [ring/ring-mock "0.3.0"]
                             [org.clojure/data.codec "0.1.0"]
                             [org.clojure/data.csv "0.1.3"]

                             ;; use client lib for testing
                             [http-kit "2.1.19"]
                             ]
              :env {:dev true}
              :source-paths ["dev"]
              :repl-options {:init-ns user}}}

:figwheel {;:server-logfile "target/logs/figwheel.log"
           :css-dirs ["resources/public/css"]}

:cljsbuild {:builds
            [
             {:id "prod"
              :source-paths ["src-cljc" "src-cljs"]
              :compiler {:output-to "resources/public/js/main_prod.js"
                         :optimizations :advanced
                         :parallel-build true}}
             ;; lein figwheel to run with auto-reloading
             ;; lein cljsbuild once to run otherwise
             {:id "dev"
              :source-paths ["src-cljc" "src-cljs"]
              :figwheel {:on-jsload "kanopi.main/reload-om"}
              :compiler {:output-to "resources/public/js/main.js"
                         :output-dir "resources/public/js/out"
                         ;; NOTE: yes leading slash here for a reason!
                         :asset-path "/js/out"
                         :main "kanopi.main"
                         :optimizations :none
                         :parallel-build true
                         :pretty-print true
                         :source-map "resources/public/js/source_map.js"}}

             ;; lein figwheel devcards to run
             {:id "devcards"
              :source-paths ["src-cljc" "src-cljs" "dev-cljs"]
              :figwheel {:devcards true}
              :compiler {:output-to "resources/public/js/main_devcards.js"
                         :output-dir "resources/public/js/out_devcards"
                         ;; NOTE: no leading slash here for a reason!
                         :asset-path "js/out_devcards"
                         :main "kanopi.devcards"
                         :optimizations :none
                         :parallel-build true
                         :pretty-print true
                         :source-map "resources/public/js/source_map_devcards.js"}}
             ]
            })

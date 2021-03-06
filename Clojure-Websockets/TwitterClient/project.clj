(defproject birdwatch-tc "0.2.0-SNAPSHOT"
  :description "Twitter client part of the BirdWatch system"
  :url "https://github.com/matthiasn/Birdwatch"
  :license {:name "GNU General Public License" :url "http://www.gnu.org/licenses/gpl-3.0.en.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha8"]
                 [clj-http "3.1.0"]
                 [twitter-api "0.7.8" :exclusions [org.clojure/data.json]]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [clojurewerkz/elastisch "2.2.1"]
                 [org.clojure/tools.logging "0.3.1"]
                 [matthiasn/systems-toolbox "0.6.1-SNAPSHOT"]
                 [matthiasn/systems-toolbox-redis "0.6.1-SNAPSHOT"]
                 [matthiasn/birdwatch-specs "0.3.1"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [clj-time "0.12.0"]
                 [clj-pid "0.1.2"]
                 [com.taoensso/carmine "2.13.1"]]
  :source-paths ["src/clj/"]
  :main ^:skip-aot birdwatch-tc.main
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

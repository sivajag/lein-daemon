(defproject org.clojars.sivajag/lein-daemon "0.3.1"
  :description "A lein plugin that daemonizes a clojure process"
  :url "https://github.com/arohner/leiningen"
  :license {:name "Eclipse Public License"}
  :repositories {"central" "http://repo1.maven.org/maven2"}
  :dependencies [[com.sun.akuma/akuma "1.4"]
                 [org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]]
  :dev-dependencies [[lein-clojars "0.6.0"]
                     [swank-clojure "1.2.1"]])

(ns leiningen.daemon
  (:use [leiningen.compile :only [eval-in-project]]
        [leiningen.core :only [abort]]
        [leiningen.help :only [help-for]])
  (:import java.io.File)
  (require [leiningen.daemon-runtime :as runtime])
  (:use [clojure.contrib.except :only (throwf)]))

(defn wait-for
  "periodically calls test, a fn of no arguments, until it returns
  true, or timeout (in seconds) exceeded. Calls fail, a fn of no
  arguments if test never returns true"
  [test fail timeout]
  (let [start (System/currentTimeMillis)
        end (+ start (* timeout 1000))]
    (while (and (< (System/currentTimeMillis) end) (not (test)))
           (Thread/sleep 1))
    (if (< (System/currentTimeMillis) end)
      true
      (fail))))

(defn get-pid-path [project alias index]
  (str (get-in project [:daemon alias :pidfile]) "_" index))

(defn pid-present? [project alias index]
  (runtime/get-pid (get-pid-path project alias index)))

(defn running? [project alias index]
  (runtime/process-running? (pid-present? project alias index)))

(defn inconsistent?
  "true if pid is present, and process not running"
  [project alias index]
  (and (pid-present? project alias index) (not (running? project alias index))))

(defn start-main
  [project alias index & args]
  (let [ns (get-in project [:daemon alias :ns])
        timeout 60]
    (if (not (pid-present? project alias index))
      (do
        (println "forking" alias)
        (eval-in-project project `(do
                                    (leiningen.daemon-runtime/init ~(get-pid-path project alias index) :debug ~(get-in project [:daemon alias :debug]))
                                    ((ns-resolve '~(symbol ns) '~'-main) ~@args))
                         (fn [java]
                           (when (not (get-in project [:daemon alias :debug]))
                             (.setSpawn java true)))
                         nil `(do
                                (System/setProperty "leiningen.daemon" "true")
                                (require 'leiningen.daemon-runtime)
                                (require '~(symbol ns)))
                         true)
        (wait-for #(running? project alias index) #(throwf "%s failed to start in %s seconds" alias timeout) timeout)
        (println "waiting for pid file to appear")
        (println alias "started")
        (System/exit 0))
      (if (running? project alias)
        (do
          (println alias "already running")
          (System/exit 1))
        (do
          (println "not starting, pid file present")
          (System/exit 2))))))

(defn stop [project alias index]
  (let [pid (runtime/get-pid (get-pid-path project alias index))
        timeout 60]
    (when (running? project alias index)
      (println "sending SIGTERM to" pid)
      (runtime/sigterm pid))
    (wait-for #(not (running? project alias index)) #(throwf "%s failed to stop in %d seconds" alias timeout) timeout)
    (-> (get-pid-path project alias index) (File.) (.delete))))

(defn check [project alias index]
  (when (running? project alias index)
    (do (println alias "is running") (System/exit 0)))
  (when (inconsistent? project alias index)
    (do (println alias "pid present, but NOT running") (System/exit 2)))
  (do (println alias "is NOT running") (System/exit 1)))

(defn check-valid-daemon [project alias index]
  (let [d (get-in project [:daemon alias])
        pid-path (str (get-in project [:daemon alias :pidfile]) "_" index)]
    (when (not d)
      (abort (str "daemon " alias " not found in :daemon section")))
    (when (not pid-path)
      (abort (str ":pidfile is required in daemon declaration")))
    true))

(declare usage)
(defn ^{:help-arglists '([])} daemon
  "Run a -main function as a daemon, with optional command-line arguments.

In project.clj, define a keyvalue pair that looks like
 :daemon {:foo {:ns foo.bar
                :pidfile \"foo.pid\"}}

USAGE: lein daemon start :foo index
USAGE: lein daemon stop :foo index
USAGE: lein daemon check :foo index

this will apply the -main method in foo.bar.

On the start call, additional arguments will be passed to -main

USAGE: lein daemon start :foo index bar baz 
"
  [project & [command daemon-name index  & args :as all-args]]
  (when (or (nil? command)
            (nil? daemon-name)
            (nil? index))
    (abort (help-for "daemon")))
  (let [command (keyword command)
        daemon-name (if (keyword? (read-string daemon-name))
                      (read-string daemon-name)
                      daemon-name)
        alias (get-in project [:daemon daemon-name])]
    (check-valid-daemon project daemon-name index)
    (condp = (keyword command)
      :start (apply start-main project daemon-name index args)
      :stop (stop project daemon-name index)
      :check (check project daemon-name index)
      (abort (str command " is not a valid command")))))
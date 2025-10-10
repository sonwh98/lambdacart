(ns user
  "Development namespace that auto-starts the server"
  (:require [lambdacart.server :as server]
            [shadow.cljs.devtools.api :as shadow]))


(defn start-dev []
  (println "Starting server development environment...")
  (server/start-server)
  (println "Server started."))

(defn stop-dev []
  (println "Stopping development environment...")
  (server/stop-server))

;; Auto-start when this namespace loads
(start-dev)



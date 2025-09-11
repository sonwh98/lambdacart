(ns user
  "Development namespace that auto-starts the server"
  (:require [lambdacart.server :as server]
            [shadow.cljs.devtools.api :as shadow]))


(defn start-dev []
  (println "Starting development environment...")
  (server/start-server)
  (println "Server started. You can now run: (shadow/watch :grid)"))

(defn stop-dev []
  (println "Stopping development environment...")
  (server/stop-server))

;; Auto-start when this namespace loads
(start-dev)



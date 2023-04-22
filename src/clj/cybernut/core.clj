(ns cybernut.core
  (:require [stigmergy.server :as server]))

(defn main [args]
  (server/start-http)
  )

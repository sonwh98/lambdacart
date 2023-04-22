(ns cybernut.core
  (:require
   [stigmergy.plumdb :as db] 
   [stigmergy.server :as server]))

(defn main [args]
  (server/start-http)
  )

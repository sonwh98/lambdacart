(ns lambdacart.core
  (:require [stigmergy.plumdb :as db]
            [stigmergy.chp]
            [stigmergy.server :as s]))

(defn default-handler [req]
  (stigmergy.chp/render "mdb-template.chp" {}))

(comment
  (s/start-http)
  (s/stop-http))

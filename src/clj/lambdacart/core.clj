(ns lambdacart.core
  (:require [stigmergy.chp]))

(defn default-handler [req]
  (stigmergy.chp/render "template.chp" {}))

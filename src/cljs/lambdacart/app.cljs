(ns lambdacart.app
  (:require [reagent.core :as r]))

(defonce state (r/atom {:grid {:rows []}}))

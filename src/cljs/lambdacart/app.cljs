(ns lambdacart.app
  (:require [reagent.core :as r]))

(defonce state 
  (doto (r/atom {:grid {:rows []}})
    (add-watch :logger
              (fn [_key _ref old-state new-state]
                (prn "State changed:"
                     {:old old-state
                      :new new-state})))))

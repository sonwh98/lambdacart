(ns lambdacart.app
  (:require [reagent.core :as r]))

(defonce state
  (doto (r/atom {:grid {:rows []}
                 :context-menu {:visible? false :x 0 :y 0}})
    (add-watch :logger
               (fn [_key _ref old-state new-state]
                 (prn "State changed:"
                      #_{:old old-state
                         :new new-state})))))


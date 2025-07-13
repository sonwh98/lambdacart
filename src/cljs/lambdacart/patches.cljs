(ns lambdacart.patches
  (:require [shadow.cljs.devtools.client.env :as env]))

#_(set! env/get-ws-relay-url
        (fn []
          (let [base "wss://vn.bumble.fish" #_(env/get-ws-url-base)
                path (env/get-ws-relay-path)]
            (prn {:base base :path path})
            (str base path))
          #_"wss://vn.bumble.fish/api/remote-relay"))

(prn "preload")

(ns lambdacart.patches
  (:require [shadow.cljs.devtools.client.env :as env]))
 
(set! env/get-ws-relay-url
      (fn []
        "wss://vn.bumble.fish/ws"))

(prn "patches")

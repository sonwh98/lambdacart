(ns lambdacart.server
  (:require [org.httpkit.server :as http]
            [stigmergy.server]
            [stigmergy.chp]
            [stigmergy.config :as c]
            [clojure.core.async :as async]
            [bidi.ring :as bidi] :reload))

(defonce clients (atom []))

(defn add-client! [channel]
  (swap! clients #(if (some #{channel} %)
                    %
                    (do
                      (prn "add-client! " %)
                      (conj % channel)))))

(defn ws-handler [req]
  (http/with-channel req channel
    (when (http/websocket? channel)
      (add-client! channel)
      (http/on-receive channel
                       (fn [data]
                         (println "Received:" data)
                         (http/send! channel (str "Echo: " data))))
      (http/on-close channel
                     (fn [status]
                       (println "Channel closed:" status))))))

(defn create-app []
  (let [routes (c/config :bidi-routes)
        handler (bidi/make-handler routes)
        mime-types (merge {"chp" "text/html"
                           nil "text/html"}
                          (c/config :mime-types))
        app (-> handler
                (ring.middleware.file/wrap-file "public")
                ring.middleware.params/wrap-params
                (ring.middleware.content-type/wrap-content-type {:mime-types mime-types}))]
    (fn [req]
      (if (= (:uri req) "/ws")
        (ws-handler req)
        (app req)))))

(defn -main []
  (println "Loading configuration in" (System/getProperty "config"))
  (c/reload)
  (let [port (c/config :port)]
    (http/run-server (create-app) {:port port})
    (println (format "Server running on http://localhost:%s with WebSocket at ws://localhost:%s/ws", port port))))

(comment)

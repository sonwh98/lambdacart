(ns lambdacart.server
  (:require [org.httpkit.server :as http]
            [stigmergy.server]
            [clojure.core.async :as async]))

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

(defn -main []
  (http/run-server ws-handler {:port 3002})
  (println "WebSocket server running on ws://localhost:3002"))

(comment)

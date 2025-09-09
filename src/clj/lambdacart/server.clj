(ns lambdacart.server
  (:require [lambdacart.serde :as serde]
            [org.httpkit.server :as http]
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

(defn broadcast [msg]
  (doseq [client @clients]
    (try
      (if (string? msg)
        (http/send! client msg)
        (let [transit-data (serde/edn->transit msg)
              bytes-data (.getBytes transit-data "UTF-8")]
          (http/send! client bytes-data)))
      (catch Exception e
        (println "Failed to send message to client:" (.getMessage e))
        (swap! clients #(remove #{client} %))))))

;; Registry for available functions
(defonce available-functions (atom {}))

(defn register-function! [fn-name fn-impl]
  (swap! available-functions assoc fn-name fn-impl))

;; Register some example functions
(register-function! 'ping 
  (fn [& args]
    (println "Ping called with args:" args)
    {:type :pong :timestamp (java.util.Date.) :args args}))

(register-function! 'echo 
  (fn [message]
    (println "Echo called with:" message)
    {:type :echo-response :message message}))

(register-function! 'update-cell 
  (fn [row col value]
    (println "Updating cell" row col "to" value)
    ;; Broadcast the change to all connected clients
    (broadcast {:type :cell-updated :row row :col col :value value})
    {:type :update-success :row row :col col :value value}))

(register-function! 'delete-rows
  (fn [row-indices]
    (println "Deleting rows:" row-indices)
    ;; Broadcast the deletion to all connected clients
    (broadcast {:type :rows-deleted :rows row-indices})
    {:type :delete-success :rows row-indices}))

(register-function! 'query-data 
  (fn [filters & [pagination]]
    (println "Querying data with filters:" filters "pagination:" pagination)
    ;; Here you would query your database
    {:type :query-results 
     :data []  ; Replace with actual query results
     :total-count 0
     :filters filters
     :pagination pagination}))

(register-function! 'broadcast-message
  (fn [message]
    (println "Broadcasting message:" message)
    (broadcast {:type :broadcast :message message :timestamp (java.util.Date.)})
    {:type :broadcast-sent}))

(register-function! 'get-stats
  (fn []
    {:type :stats-response 
     :connected-clients (count @clients)
     :available-functions (keys @available-functions)
     :server-time (java.util.Date.)}))

(defn send-response [channel response]
  (when response
    (let [response-data (serde/edn->transit response)
          response-bytes (.getBytes response-data "UTF-8")]
      (http/send! channel response-bytes))))

(defn send-error [channel message]
  (let [error-response {:type :error :message message}
        error-data (serde/edn->transit error-response)
        error-bytes (.getBytes error-data "UTF-8")]
    (http/send! channel error-bytes)))

(defn invoke [data channel]
  (try
    (if (and (sequential? data) (not (empty? data)))
      (let [fn-name (first data)
            args (rest data)
            fn-impl (get @available-functions fn-name)]
        (if fn-impl
          (do
            (println "Calling function:" fn-name "with args:" args)
            (let [result (apply fn-impl args)]
              (prn "sending result to client " result)
              (send-response channel result)))
          (do
            (println "Unknown function:" fn-name)
            (send-error channel (str "Unknown function: " fn-name ". Available: " 
                                    (keys @available-functions))))))
      (do
        (println "Invalid message format. Expected (fn-name arg1 arg2 ...), got:" data)
        (send-error channel "Invalid message format. Expected (fn-name arg1 arg2 ...)")))
    (catch Exception e
      (println "Error invoking function:" (.getMessage e))
      (.printStackTrace e)
      (send-error channel (str "Server error: " (.getMessage e))))))

(defn ws-handler [req]
  (http/with-channel req channel
    (when (http/websocket? channel)
      (add-client! channel)
      (http/on-receive channel
                       (fn [transit-data]
                         (try
                           (let [edn-data (if (bytes? transit-data)
                                            ;; Handle binary data
                                            (let [json-str (String. transit-data "UTF-8")]
                                              (serde/transit->edn json-str))
                                            ;; Handle text data
                                            (serde/transit->edn transit-data))]
                             (println "Received function call:" edn-data)
                             (invoke edn-data channel))
                           (catch Exception e
                             (println "Error processing WebSocket data:" (.getMessage e))
                             (send-error channel "Invalid data format")))))
      (http/on-close channel
                     (fn [status]
                       (println "Channel closed:" status)
                       (swap! clients #(remove #{channel} %)))))))

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
      (if (= (:uri req) "/wsstream")
        (ws-handler req)
        (app req)))))

(defonce server (atom nil))

(defn start-server []
  (println "Loading configuration in" (System/getProperty "config"))
  (c/reload)
  (let [port (c/config :port)
        s (http/run-server (create-app) {:port port})]
    (reset! server s)
    (println (format "Server running on http://localhost:%s with WebSocket at ws://localhost:%s/wsstream" port port))
    (println "Available RPC functions:" (keys @available-functions))))

(defn stop-server []
  (when @server
    (println "Stopping server...")
    (@server) ; Call the server function to stop it
    (reset! server nil)
    (reset! clients []) ; Clear all connected clients
    (println "Server stopped.")))

(defn -main []
  (start-server))

(comment
  (start-server)
  (stop-server)
  (broadcast "wassup")
  (broadcast {:type :notification :message "Hello all clients!"})
  (count @clients)
  
  ;; Check available functions
  @available-functions
  
  ;; Register new functions at runtime
  (register-function! 'add
    (fn [a b]
      {:type :math-result :operation :add :result (+ a b)}))
  
  (register-function! 'get-time
    (fn []
      {:type :time-response :time (java.util.Date.)})))

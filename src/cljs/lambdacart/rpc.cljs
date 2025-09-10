(ns lambdacart.rpc
  (:require [lambdacart.stream :as stream]
            [lambdacart.app :as app]
            [cljs.core.async :refer [chan put! <! >!] :as async]))

(defonce pending-requests (atom {}))

(defn generate-request-id []
  (str (random-uuid)))

(defn invoke [fn-name & args]
  (let [wss (-> @app/state :wss)
        fn-call (cons fn-name args)]
    (if wss
      (do
        (js/console.log "Invoking remote function:" fn-name "with args:" args)
        (stream/write wss fn-call {}))
      (js/console.error "WebSocket not available. Make sure to call init! first."))))

(defn invoke-async [fn-name & args]
  (let [wss (-> @app/state :wss)
        fn-call (cons fn-name args)]
    (if wss
      (do
        (js/console.log "Invoking remote function (async):" fn-name "with args:" args)
        (stream/write wss fn-call {})
        ;; Return a go block that will contain the response
        (stream/read wss {:as :value}))
      (do
        (js/console.error "WebSocket not available. Make sure to call init! first.")
        (async/go nil)))))

(defn invoke-with-response [fn-name & args]
  (let [wss (-> @app/state :wss)
        request-id (generate-request-id)
        fn-call {:request-id request-id
                 :function fn-name
                 :args (vec args)}
        response-chan (chan 1)]
    (if wss
      (do
        (js/console.log "Invoking function with ID:" fn-name request-id)
        (swap! pending-requests assoc request-id response-chan)
        (stream/write wss fn-call {})
        response-chan)
      (do
        (js/console.error "WebSocket not available")
        (async/go nil)))))

(defn handle-broadcast [response]
  (case (:type response)
    :cell-updated (do
                    (js/console.log "Cell updated broadcast")
                    (swap! app/state assoc-in [:grid :rows (:row response) (:col response)] (:value response)))
    :broadcast (js/console.log "Broadcast:" (:message response))
    (js/console.log "Unhandled broadcast:" response)))

(defn start-response-handler [wss]
  (js/console.log "Starting response handler...")
  (async/go-loop []
    (js/console.log "Waiting for response...")
    (when-let [response (<! (stream/read wss {:as :value}))]
      (js/console.log "Response handler received:" response)
      (js/console.log "Response type:" (type response))
      (js/console.log "Request ID in response:" (:request-id response))
      (js/console.log "Pending requests:" @pending-requests)

      (if-let [request-id (:request-id response)]
        ;; Handle correlated response
        (if-let [response-chan (get @pending-requests request-id)]
          (do
            (js/console.log "Found matching request, sending response")
            (put! response-chan response)
            (swap! pending-requests dissoc request-id))
          (js/console.warn "No pending request for ID:" request-id))
        ;; Handle broadcasts and notifications
        (do
          (js/console.log "Handling as broadcast")
          (handle-broadcast response)))
      (recur))))

(defn load-grid-data []
  "Load grid data via RPC and return the response channel"
  (invoke-with-response 'q '[:find [(pull ?e [*]) ...]
                             :where
                             [?e :item/name _]]))

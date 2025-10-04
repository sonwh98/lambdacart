;; filepath: /Users/sto/workspace/lambdacart/src/cljs/lambdacart/stream.cljs
(ns lambdacart.stream
  (:require [lambdacart.serde :as serde]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]))

(defprotocol Stream
  (open [this params] "Open the stream with param which has only 1 mandatory key :path to a resource in a graph")
  (read [this params] "Read from the stream with params")
  (write [this data params] "Write data to the stream with")
  (close [this] "Close the stream.")
  (status [this] "Returns the current status of the stream, e.g., :connecting, :connected."))

(defrecord WebSocketStream [url ws in out state-atom]
  Stream
  (open [this _]
    (let [in-chan (chan)
          out-chan (chan)
          internal-state-atom (atom {:attempt 0 :status :connecting})
          connect! (fn connect-fn! []
                     (let [full-url (if (re-matches #"^wss?://.*" url)
                                      url
                                      (str "//" (.-host js/location) url))
                           new-ws (js/WebSocket. full-url)]
                       (set! (.-onopen new-ws)
                             (fn [_]
                               (js/console.log "WebSocket connection opened.")
                               (reset! internal-state-atom {:attempt 0 :status :connected})))
                       (set! (.-onmessage new-ws)
                             (fn [event]
                               (try
                                 (let [raw-data (.-data event)
                                       edn-data (serde/transit->edn raw-data)] ; Use in-chan
                                   (async/put! in-chan edn-data))
                                 (catch js/Error e
                                   (js/console.error "Error parsing transit message:" e)
                                   (async/put! in (.-data event))))))
                       (set! (.-onclose new-ws)
                             (fn [event]
                               (js/console.warn "WebSocket connection closed. Reconnecting...")
                               (swap! internal-state-atom update :attempt inc)
                               (let [delay-ms (* (min 60 (* 2 (:attempt @internal-state-atom))) 1000)]
                                 (js/console.log (str "Reconnecting in " (/ delay-ms 1000) "s..."))
                                 (js/setTimeout connect-fn! delay-ms))))
                       (set! (.-onerror new-ws)
                             (fn [error]
                               (js/console.error "WebSocket error:" error)))
                       (assoc this :ws new-ws :in in-chan :out out-chan :state-atom internal-state-atom)))]
      (let [new-this (connect!)] ; This returns the stream with the internal state-atom
        ;; This go-loop handles messages from the `out` channel and sends them
        ;; through the WebSocket. It's independent of the connection status.
        (async/go-loop []
          (when-let [msg (<! (:out new-this))]
            (when-let [current-ws (:ws new-this)]
              (when (= (.-readyState current-ws) 1) ; 1 = OPEN
                (.send current-ws msg)))
            (recur)))
        new-this)))

  (read [this {:keys [as timeout-ms] :or {as :channel}}]
    (let [in-stream (:in this)]
      (case as
        :channel in-stream
        :value (async/go
                 (if timeout-ms
                   (let [[val port] (async/alts! [in-stream (async/timeout timeout-ms)])]
                     (if (= port in-stream)
                       val
                       (do
                         (js/console.log "Stream read timeout after" timeout-ms "ms")
                         ::timeout))) ; Return a keyword to indicate timeout
                   (<! in-stream))))))

  (write [this edn-data params]
    (let [out-stream (:out this)
          {:keys [callback] :or {callback nil}} params
          transit-data (serde/edn->transit edn-data)]
      (if callback
        (put! out-stream transit-data callback)
        (put! out-stream transit-data))))

  (close [this]
    ;; Prevent reconnection by setting a flag
    (swap! (:state-atom this) assoc :status :closed)
    (when-let [ws (:ws this)]
      (set! (.-onclose ws) nil) ; Remove onclose handler to stop reconnection
      (.close ws))
    (close! (:in this))
    (close! (:out this)))

  (status [this]
    @(:state-atom this)))

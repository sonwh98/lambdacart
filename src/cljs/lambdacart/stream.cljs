;; filepath: /Users/sto/workspace/lambdacart/src/cljs/lambdacart/stream.cljs
(ns lambdacart.stream
  (:require [lambdacart.serde :as serde]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]))

(defprotocol Stream
  (open [this params] "Open the stream with param which has only 1 mandatory key :path to a resource in a graph")
  (read [this params] "Read from the stream with params")
  (write [this data params] "Write data to the stream with")
  (close [this] "Close the stream."))

(defrecord WebSocketStream [url ws in out]
  Stream
  (open [this _]
    (let [full-url (if (re-matches #"^wss?://.*" url)
                     url ; Already has protocol
                     (let [protocol (if (= (.-protocol js/location) "https:")
                                      "wss:"
                                      "ws:")
                           host (.-host js/location)]
                       (str protocol "//" host url)))
          ws (js/WebSocket. full-url)
          in (chan 10)
          out (chan 10)]
      (set! (.-onopen ws)
            (fn [_]
              (js/console.log "WebSocket connection opened")))
      (set! (.-onmessage ws)
            (fn [event]
              (try
                ;; Parse the transit data before putting it in the channel
                (let [raw-data (.-data event)
                      edn-data (serde/transit->edn raw-data)]
                  (js/console.log "Received and parsed message:" edn-data)
                  (put! in edn-data))
                (catch js/Error e
                  (js/console.error "Error parsing transit message:" e)
                  (js/console.log "Raw data was:" (.-data event))
                  ;; Put raw data as fallback
                  (put! in (.-data event))))))
      (set! (.-onclose ws)
            (fn [_]
              (js/console.log "WebSocket connection closed")
              (close! in)
              (close! out)))
      (set! (.-onerror ws)
            (fn [error]
              (js/console.error "WebSocket error:" error)
              (close! in)
              (close! out)))
      (async/go-loop []
        (when-let [msg (<! out)]
          (when (= (.-readyState ws) 1)
            (.send ws msg))
          (recur)))
      (assoc this :ws ws :in in :out out)))

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
    (when-let [ws (:ws this)] (.close ws))
    (close! (:in this))
    (close! (:out this))))
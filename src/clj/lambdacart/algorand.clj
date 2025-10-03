(ns lambdacart.algorand
  (:require [clojure.core.async :as async :refer [go-loop timeout alts!]]
            [clojure.data.json :as json])
  (:import [java.util Base64]
           [java.net URI]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]))

(def indexer-base "https://mainnet-idx.algonode.cloud")
(defonce client (HttpClient/newHttpClient))

(defn decode-note
  "If tx has a base64 :note field, decode it and assoc it as :note-decoded.
   Returns the tx with the new key."
  [tx]
  (if-let [note-b64 (:note tx)]
    (let [decoder (Base64/getDecoder)
          bytes   (.decode decoder note-b64)
          decoded (String. bytes "UTF-8")]
      (assoc tx :note-decoded decoded))
    tx))

(defn fetch-transactions
  "Returns a vector of transactions for `address`, or nil on error."
  [address]
  (try
    (let [uri (URI. (str indexer-base "/v2/transactions?address=" address "&limit=25"))
          request (-> (HttpRequest/newBuilder uri)
                      (.header "Accept" "application/json")
                      (.build))
          response (.send client request (HttpResponse$BodyHandlers/ofString))]
      (when (= 200 (.statusCode response))
        (-> (.body response)
            (json/read-str :key-fn keyword)
            :transactions)))
    (catch Exception e
      (println "Error fetching transactions:" (.getMessage e))
      nil)))

(defn- now-epoch-seconds [] (quot (System/currentTimeMillis) 1000))

(defn monitor-address
  "Polls every `interval-ms` (default 15000). Emits only txs whose :round-time
   is strictly greater than the moving baseline (start-epoch), which advances
   each tick to the max seen round-time."
  [address on-tx & {:keys [interval-ms] :or {interval-ms 15000}}]
  (let [stop (async/chan)]
    (go-loop [start-epoch (now-epoch-seconds)]
      (let [[_ ch] (alts! [(timeout interval-ms) stop])]
        (when (not= ch stop)
          (let [txs (fetch-transactions address)
                ;; txs may be nil; compute max rt we observed this tick
                seen-max-rt (reduce (fn [m tx]
                                      (let [rt (:round-time tx)]
                                        (if (number? rt) (max m rt) m)))
                                    start-epoch
                                    (or txs []))]
            (when txs
              (doseq [tx txs
                      :let [rt (:round-time tx)
                            tx (decode-note tx)]
                      :when (and rt (> (long rt) start-epoch))]
                (try
                  (on-tx tx)
                  (catch Throwable e
                    (println "on-tx handler error:" (.getMessage e))))))
            ;; advance baseline and continue
            (recur seen-max-rt)))))
    {:stop #(async/close! stop)}))

(comment
  (fetch-transactions "F7YGGVYNO6NIUZ35UTQQ7GMQPUOELTERYHGGLESYSABC6E5P2ZYMRJPWOQ")
  
  (def watcher
    (lambdacart.algorand/monitor-address
     "F7YGGVYNO6NIUZ35UTQQ7GMQPUOELTERYHGGLESYSABC6E5P2ZYMRJPWOQ"
     (fn [tx]
       (clojure.pprint/pprint tx)
       #_(println "NEW tx after start:" (:id tx) "amt:" (get-in tx [:payment-transaction :amount])))
     :interval-ms 5000))

  ;; later:
  ((:stop watcher))
  )

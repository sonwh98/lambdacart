(ns lambdacart.algorand
  (:require [clojure.core.async :as async :refer [go-loop timeout alts!]]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import [java.util Base64]
           [java.net URI]
           [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]
           [java.time Instant]
           [java.net URLEncoder]))

(def indexer-base "https://mainnet-idx.algonode.cloud")
(defonce client (HttpClient/newHttpClient))

(defn- now-epoch-seconds [] (quot (System/currentTimeMillis) 1000))

(defn fetch-transactions
  "Returns a vector of transactions. Expects a map with :address and optional :since (epoch seconds)."
  [{:keys [address since]}]
  (try
    (prn "fetch-transactions" address " " since)
    (let [base-url (str indexer-base "/v2/transactions")
          ;; Build query params, adding after-time if :since is provided
          query-params (cond-> {"address" address, "limit" 25}
                         since (assoc "after-time" (-> since Instant/ofEpochSecond .toString)))
          ;; URL-encode and join the query parameters
          query-string (str/join "&"
                                 (for [[k v] query-params]
                                   (str (URLEncoder/encode (name k) "UTF-8")
                                        "="
                                        (URLEncoder/encode (str v) "UTF-8"))))
          uri (URI. (str base-url "?" query-string))
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

(comment
  ;; Fetch recent transactions for an address
  (fetch-transactions {:address "F7YGGVYNO6NIUZ35UTQQ7GMQPUOELTERYHGGLESYSABC6E5P2ZYMRJPWOQ"})

  ;; Fetch transactions for an address since a specific time (e.g., last 24 hours)
  (def tx (let [one-day-ago (- (now-epoch-seconds) (* 0.5 60 60))]
            (fetch-transactions {:address "F7YGGVYNO6NIUZ35UTQQ7GMQPUOELTERYHGGLESYSABC6E5P2ZYMRJPWOQ" :since one-day-ago})))
  (clojure.pprint/pprint tx))

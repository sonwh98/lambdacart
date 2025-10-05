(ns lambdacart.wallet
  (:require [reagent.core :as r]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]
            [lambdacart.app :as app]
            [lambdacart.rpc :as rpc]
            [lambdacart.stream :as stream]
            [cljs.pprint :refer [pprint]]
            [clojure.string :as str]
            ["algosdk" :as algosdk]
            ["bip39" :as bip39]
            ["qrcode" :as qrcode]
            ["@perawallet/connect" :refer [PeraWalletConnect]]))

(defn generate-payment-qr-code [address]
  (let [qr-ref (r/atom nil)]
    (r/create-class
     {:display-name "QRCode"
      :component-did-mount
      (fn [this]
        (when-let [el @qr-ref]
          (.toCanvas qrcode el address #js {:width 180 :margin 2})))
      :component-did-update
      (fn [this]
        (when-let [el @qr-ref]
          (set! (.-width el) 0) ; clear canvas
          (.toCanvas qrcode el address #js {:width 180 :margin 2})))
      :reagent-render
      (fn [_]
        [:canvas {:ref #(reset! qr-ref %)}])})))

(defn generate-algorand-account
  "Generate a new Algorand account (25-word mnemonic)"
  []
  (let [account (.generateAccount algosdk)
        mnemonic (.secretKeyToMnemonic algosdk (.-sk account))
        words (str/split mnemonic #"\s+")
        indexed-words (map-indexed (fn [idx w]
                                     [(inc idx) w])
                                   words)]
    {:address (.-addr account)
     :secret-key (.-sk account)
     :mnemonic indexed-words
     :type :algorand-native}))

(defn- now-epoch-seconds [] (quot (.getTime (js/Date.)) 1000))

(defn- decode-tx-note
  "Decodes the base64 :note field of a transaction and adds it as :note-decoded."
  [tx]
  (if-let [note (:note tx)]
    (try
      (assoc tx :note-decoded (js/atob note))
      (catch js/Error e
        (js/console.warn "Failed to decode base64 note:" note e)
        (assoc tx :note-decoded :decode-error)))
    tx))

(defn monitor-transactions
  "Monitors an Algorand address for new transactions via RPC.
  Returns a map with a :stop channel to halt monitoring."
  [address callback {:keys [interval-ms] :or {interval-ms 5000}}]
  (let [stop (async/chan)]
    (async/go-loop [since (now-epoch-seconds)]
      (let [[v ch] (async/alts! [stop (async/timeout interval-ms)])]
        (js/console.log "monitor-transactions tick, v=" v)
        (if (= ch stop)
          (js/console.log "Stopping transaction monitor for" address)
          (let [wss (-> @app/state :wss)]
            (if (and wss (stream/connected? wss))
              (let [[response _] (async/alts! [(rpc/invoke-with-response 'fetch-transactions {:address address :since since})
                                               (async/timeout 4000)])]
                (if response
                  (let [raw-txs response ;;(:results response) ;;TODO refactor should not need :results
                        decoded-txs (map #(-> % decode-tx-note decode-tx-note) raw-txs)]
                    (when (seq decoded-txs)
                      (callback decoded-txs))
                    (let [max-rt (when (seq raw-txs)
                                   (apply max (map :round-time raw-txs)))]
                      (recur (or max-rt since))))
                  (do (js/console.warn "fetch-transactions timed out, retrying...")
                      (recur since))))
              (do
                (js/console.warn "WebSocket disconnected, waiting before retrying fetch-transactions...")
                (recur since)))))))
    {:stop stop}))

(defonce pera-wallet (atom nil))

(defn init-pera-wallet []
  (when-not @pera-wallet
    (try
      (reset! pera-wallet (PeraWalletConnect.))
      (catch js/Error e
        (js/console.error "Failed to initialize PeraWallet:" e)
        nil))))

(defn pera-wallet-connect-component []
  (let [connected-address (r/atom nil)
        error-msg (r/atom nil)
        connecting? (r/atom false)
        wallet-initialized? (init-pera-wallet)]
    (fn []
      [:div {:style {:padding "20px"
                     :background-color "white"
                     :margin "20px auto"
                     :border-radius "8px"
                     :max-width "600px"
                     :text-align "center"}}
       [:h2 {:style {:margin-bottom "16px"}} "PeraWallet Connect"]

       (if-not @pera-wallet
         ;; PeraWallet not available
         [:div {:style {:padding "20px"
                        :background "#fff3cd"
                        :border-left "5px solid #ffc107"
                        :border-radius "4px"
                        :color "#856404"}}
          [:div {:style {:font-weight "bold" :margin-bottom "8px"}}
           "PeraWallet Unavailable"]
          [:div {:style {:font-size "0.9em"}}
           "PeraWallet Connect requires HTTPS. Please make sure you're accessing this page over a secure connection (https://) or use localhost for development."]]

         (if @connected-address
         ;; Connected state
           [:div
            [:div {:style {:padding "20px"
                           :background "#e8f5e9"
                           :border-left "5px solid #4caf50"
                           :margin-bottom "20px"
                           :border-radius "4px"}}
             [:div {:style {:font-weight "bold" :color "#2e7d32" :margin-bottom "8px"}}
              "Connected!"]
             [:div {:style {:font-family "monospace"
                            :font-size "0.9em"
                            :word-break "break-all"
                            :color "#555"}}
              @connected-address]]

            [:button {:on-click (fn []
                                  (-> @pera-wallet
                                      (.disconnect)
                                      (.then #(do
                                                (reset! connected-address nil)
                                                (reset! error-msg nil)))
                                      (.catch #(reset! error-msg (str "Disconnect error: " (.-message %))))))
                      :style {:background "#ff4444"
                              :color "white"
                              :border "none"
                              :border-radius "5px"
                              :padding "12px 24px"
                              :cursor "pointer"
                              :font-size "1em"}}
             "Disconnect"]]

         ;; Not connected state
           [:div
            [:p {:style {:margin "16px 0" :color "#666"}}
             "Connect your PeraWallet by scanning the QR code that appears in the popup."]

            [:button {:on-click (fn []
                                  (reset! connecting? true)
                                  (reset! error-msg nil)
                                  (-> @pera-wallet
                                      (.connect)
                                      (.then (fn [accounts]
                                               (when (seq accounts)
                                                 (reset! connected-address (first accounts))
                                                 (reset! connecting? false))))
                                      (.catch (fn [error]
                                                (reset! error-msg (str "Connection error: " (.-message error)))
                                                (reset! connecting? false)))))
                      :disabled @connecting?
                      :style {:background (if @connecting? "#aaa" "#e91e63")
                              :color "white"
                              :border "none"
                              :border-radius "5px"
                              :padding "12px 24px"
                              :cursor (if @connecting? "not-allowed" "pointer")
                              :font-size "1em"}}
             (if @connecting? "Connecting..." "Connect PeraWallet")]]))

       (when @error-msg
         [:div {:style {:margin-top "16px"
                        :padding "12px"
                        :background "#ffebee"
                        :border-left "5px solid #f44336"
                        :color "#c62828"
                        :text-align "left"
                        :border-radius "4px"}}
          @error-msg])])))

(defn wallet-component []
  [:div])

(comment
  (generate-algorand-account)

  ;; Example of monitoring an address for transactions.
  ;; The callback will be invoked with a vector of new transactions.
  (def monitor (monitor-transactions
                "F7YGGVYNO6NIUZ35UTQQ7GMQPUOELTERYHGGLESYSABC6E5P2ZYMRJPWOQ"
                (fn [txs]
                  (js/alert "Payment received:")
                  (cljs.pprint/pprint txs))
                {:interval-ms 5000}))

  (async/close! (:stop monitor)))

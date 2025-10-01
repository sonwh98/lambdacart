
(ns lambdacart.wallet
  (:require [reagent.core :as r]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]
            [cljs.pprint :refer [pprint]]
            [clojure.string :as str]
            ["algosdk" :as algosdk]
            ["bip39" :as bip39]
            ["qrcode" :as qrcode]))

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

(defn wallet-component []
  [:div])

(comment
  (generate-algorand-account))

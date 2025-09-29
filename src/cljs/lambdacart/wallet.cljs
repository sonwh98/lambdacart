(ns lambdacart.wallet
  (:require [reagent.core :as r]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]
            [cljs.pprint :refer [pprint]]
            [clojure.string :as str]
            ["algosdk" :as algosdk]
            ["bip39" :as bip39]))

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

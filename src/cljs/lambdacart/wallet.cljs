(ns lambdacart.wallet
  (:require [reagent.core :as r]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]
            [cljs.pprint :refer [pprint]]
            ["algosdk" :as algosdk]))

;; Algorand client configuration
(def algod-token "")
(def algod-server "https://testnet-api.algonode.cloud")
(def algod-port "")

(defn create-algod-client []
  "Create Algorand client for testnet"
  (new algosdk/Algodv2 algod-token algod-server algod-port))

;; Wallet functions
(defn generate-algorand-wallet []
  "Generate a new Algorand wallet with account and mnemonic"
  (try
    (let [account (.generateAccount algosdk)
          address (str (.-addr account))  ; Ensure it's a string
          secret-key (.-sk account)
          mnemonic (.secretKeyToMnemonic algosdk secret-key)]
      (pprint {:action "Generated Algorand wallet" :address address})
      {:address address
       :secret-key secret-key
       :mnemonic mnemonic
       :success true})
    (catch js/Error e
      (pprint {:error "Failed to generate Algorand wallet" :exception (.-message e)})
      {:success false :error (.-message e)})))

(defn restore-wallet-from-mnemonic [mnemonic]
  "Restore Algorand wallet from mnemonic phrase"
  (try
    (let [trimmed-mnemonic (.trim mnemonic)
          secret-key (.mnemonicToSecretKey algosdk trimmed-mnemonic)
          account (.accountFromSecretKey algosdk secret-key)
          address (str (.-addr account))]  ; Ensure it's a string
      (pprint {:action "Restored Algorand wallet from mnemonic" :address address})
      {:address address
       :secret-key secret-key
       :mnemonic trimmed-mnemonic
       :success true})
    (catch js/Error e
      (pprint {:error "Failed to restore wallet from mnemonic" :exception (.-message e)})
      {:success false :error (.-message e)})))

(defn promise-to-channel [promise]
  "Convert a JavaScript Promise to a core.async channel"
  (let [ch (async/chan)]
    (.then promise
           #(async/put! ch {:success true :result %})
           #(async/put! ch {:success false :error %}))
    ch))

(defn get-account-info [address]
  "Get account information from Algorand network"
  (let [result-chan (async/chan)]
    (try
      (let [algod-client (create-algod-client)]
        (-> (.accountInformation algod-client address)
            (.then (fn [account-info]
                     (let [info (js->clj account-info :keywordize-keys true)]
                       (pprint {:action "Retrieved account info" :address address :balance (:amount info)})
                       (async/put! result-chan {:success true :account-info info}))))
            (.catch (fn [error]
                      (pprint {:error "Failed to get account info" :exception (.-message error)})
                      (async/put! result-chan {:success false :error (.-message error)})))))
      (catch js/Error e
        (pprint {:error "Failed to get account info" :exception (.-message e)})
        (async/put! result-chan {:success false :error (.-message e)})))
    result-chan))

(defn format-algo-balance [microalgos]
  "Convert microalgos to ALGO with proper formatting"
  (if microalgos
    (.toFixed (/ microalgos 1000000) 6)
    "0.000000"))

(defn copy-to-clipboard [text]
  "Copy text to clipboard"
  (when (and js/navigator (.-clipboard js/navigator))
    (.writeText (.-clipboard js/navigator) text)
    (js/alert "Copied to clipboard!")))

;; Wallet UI Components
(defn wallet-info-display [wallet-state]
  "Display wallet information"
  [:div.wallet-info
   {:style {:background "#f8f9fa"
            :padding "20px"
            :border-radius "8px"
            :margin-bottom "20px"}}
   
   [:h4 "Wallet Information"]
   
   ;; Address
   [:div.address-section
    {:style {:margin-bottom "15px"}}
    [:label {:style {:font-weight "bold" :display "block" :margin-bottom "5px"}}
     "Address:"]
    [:div {:style {:display "flex" :align-items "center" :gap "10px"}}
     [:code {:style {:background "white"
                     :padding "8px"
                     :border "1px solid #ddd"
                     :border-radius "4px"
                     :font-size "0.9em"
                     :flex "1"
                     :word-break "break-all"}}
      (:address @wallet-state)]
     [:button {:style {:background "#007bff" :color "white" :border "none"
                       :padding "6px 12px" :border-radius "4px" :cursor "pointer"}
               :on-click #(copy-to-clipboard (:address @wallet-state))}
      "Copy"]]]
   
   ;; Balance
   [:div.balance-section
    {:style {:margin-bottom "15px"}}
    [:label {:style {:font-weight "bold" :display "block" :margin-bottom "5px"}}
     "Balance:"]
    [:div {:style {:display "flex" :align-items "center" :gap "10px"}}
     [:span {:style {:font-size "1.2em" :color "#28a745" :font-weight "bold"}}
      (format-algo-balance (:balance @wallet-state)) " ALGO"]
     [:button {:style {:background "#28a745" :color "white" :border "none"
                       :padding "6px 12px" :border-radius "4px" :cursor "pointer"}
               :on-click #(async/go
                            (let [result (<! (get-account-info (:address @wallet-state)))]
                              (when (:success result)
                                (swap! wallet-state assoc :balance 
                                       (get-in result [:account-info :amount])))))}
      "Refresh"]]]
   
   ;; Mnemonic (hidden by default)
   [:div.mnemonic-section
    [:label {:style {:font-weight "bold" :display "block" :margin-bottom "5px"}}
     "Recovery Phrase:"]
    [:details
     [:summary {:style {:cursor "pointer" :color "#dc3545" :font-weight "bold"}}
      "⚠️ Show Recovery Phrase (Keep Secret!)"]
     [:div {:style {:background "white"
                    :padding "15px"
                    :border "2px solid #dc3545"
                    :border-radius "4px"
                    :margin-top "10px"
                    :font-family "monospace"
                    :word-break "break-word"
                    :line-height "1.5"}}
      [:p {:style {:color "#dc3545" :font-weight "bold" :margin-bottom "10px"}}
       "⚠️ NEVER share this phrase with anyone!"]
      [:div (:mnemonic @wallet-state)]
      [:button {:style {:background "#007bff" :color "white" :border "none"
                        :padding "6px 12px" :border-radius "4px" :cursor "pointer"
                        :margin-top "10px"}
                :on-click #(copy-to-clipboard (:mnemonic @wallet-state))}
       "Copy Mnemonic"]]]]])

(defn wallet-actions [wallet-state]
  "Wallet action buttons"
  [:div.wallet-actions
   {:style {:display "flex" :gap "10px" :margin-top "20px"}}
   
   [:button {:style {:background "#dc3545" :color "white" :border "none"
                     :padding "10px 20px" :border-radius "4px" :cursor "pointer"}
             :on-click #(when (js/confirm "Are you sure you want to remove this wallet? Make sure you have saved your recovery phrase!")
                          (reset! wallet-state {:address nil :mnemonic nil :balance 0 :secret-key nil}))}
    "Remove Wallet"]
   
   [:button {:style {:background "#6c757d" :color "white" :border "none"
                     :padding "10px 20px" :border-radius "4px" :cursor "pointer"}
             :on-click #(js/window.open (str "https://testnet.algoexplorer.io/address/" (:address @wallet-state)))}
    "View on Explorer"]])

(defn wallet-creation-form [wallet-state]
  "Form to create or restore wallet"
  (let [mnemonic-input (r/atom "")
        creating (r/atom false)
        restoring (r/atom false)]
    (fn []
      [:div.wallet-creation
       {:style {:background "#f8f9fa"
                :padding "20px"
                :border-radius "8px"}}
       
       [:h4 "Create or Restore Wallet"]
       
       ;; Create new wallet
       [:div.create-section
        {:style {:margin-bottom "30px"}}
        [:h5 "Create New Wallet"]
        [:p "Generate a brand new Algorand wallet with a new address and recovery phrase."]
        [:button {:style {:background "#28a745" :color "white" :border "none"
                          :padding "12px 24px" :border-radius "4px" :cursor "pointer"
                          :width "100%"}
                  :disabled @creating
                  :on-click #(do
                               (reset! creating true)
                               (let [result (generate-algorand-wallet)]
                                 (if (:success result)
                                   (do
                                     (reset! wallet-state result)
                                     (async/go
                                       (let [balance-result (<! (get-account-info (:address result)))]
                                         (when (:success balance-result)
                                           (swap! wallet-state assoc :balance 
                                                  (get-in balance-result [:account-info :amount]))))))
                                   (js/alert (str "Failed to create wallet: " (:error result))))
                                 (reset! creating false)))}
         (if @creating "Creating..." "Create New Wallet")]]
       
       ;; Restore from mnemonic
       [:div.restore-section
        [:h5 "Restore from Recovery Phrase"]
        [:p "Enter your 25-word recovery phrase to restore an existing wallet."]
        [:textarea {:value @mnemonic-input
                    :on-change #(reset! mnemonic-input (-> % .-target .-value))
                    :placeholder "Enter your 25-word recovery phrase here..."
                    :style {:width "100%" :height "100px" :margin-bottom "10px"
                            :padding "10px" :border "1px solid #ccc" :border-radius "4px"
                            :font-family "monospace" :resize "vertical"}}]
        [:button {:style {:background "#007bff" :color "white" :border "none"
                          :padding "12px 24px" :border-radius "4px" :cursor "pointer"
                          :width "100%"}
                  :disabled (or @restoring (empty? (.trim @mnemonic-input)))
                  :on-click #(do
                               (reset! restoring true)
                               (let [result (restore-wallet-from-mnemonic @mnemonic-input)]
                                 (if (:success result)
                                   (do
                                     (reset! wallet-state result)
                                     (reset! mnemonic-input "")
                                     (async/go
                                       (let [balance-result (<! (get-account-info (:address result)))]
                                         (when (:success balance-result)
                                           (swap! wallet-state assoc :balance 
                                                  (get-in balance-result [:account-info :amount]))))))
                                   (js/alert (str "Failed to restore wallet: " (:error result))))
                                 (reset! restoring false)))}
         (if @restoring "Restoring..." "Restore Wallet")]]])))

;; Main wallet component
(defn wallet-component []
  "Main wallet component"
  (let [wallet-state (r/atom {:address nil :mnemonic nil :balance 0 :secret-key nil})]
    (fn []
      [:div.wallet-container
       {:style {:max-width "800px" :margin "0 auto" :padding "20px"}}
       
       [:div.wallet-header
        {:style {:text-align "center" :margin-bottom "30px"}}
        [:h2 "Algorand Wallet"]
        [:p "Create, restore, and manage your Algorand wallet"]]
       
       (if (:address @wallet-state)
         ;; Wallet exists - show wallet info and actions
         [:div
          [wallet-info-display wallet-state]
          [wallet-actions wallet-state]]
         
         ;; No wallet - show creation form
         [wallet-creation-form wallet-state])
       
       ;; Testnet notice
       [:div.testnet-notice
        {:style {:background "#fff3cd" :border "1px solid #ffeaa7"
                 :padding "15px" :border-radius "4px" :margin-top "30px"
                 :text-align "center"}}
        [:strong "⚠️ Testnet Only"]
        [:p {:style {:margin "5px 0 0 0"}}
         "This wallet is connected to Algorand Testnet. Do not send real ALGO to these addresses."]]])))

;; Export the main component
(defn mount-wallet []
  "Mount the wallet component"
  [wallet-component])

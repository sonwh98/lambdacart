(ns lambdacart.main
  (:require [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]
            [lambdacart.app :as app]
            [lambdacart.grid :as grid]
            [lambdacart.rpc :as rpc]
            [lambdacart.stream :as stream]
            [lambdacart.wallet :as wallet]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [goog.string :as gstring]
            [goog.string.format]
            [cljs.pprint :refer [pprint]]))

(defn get-fragment []
  "Get the fragment identifier from the URL"
  (let [fragment (.-hash js/location)]
    (if (and fragment (> (.-length fragment) 1))
      (.substring fragment 1) ; Remove the # character
      "default"))) ; Default view

(defn set-view-from-fragment []
  "Set the :view key in app state based on URL fragment"
  (let [fragment (get-fragment)]
    (swap! app/state assoc :view (keyword fragment))))

(defn handle-hash-change []
  "Handle browser hash change events"
  (set-view-from-fragment))

(defn get-all-items [store]
  "Get all items from all catalogs and tagories in store data"
  (->> store
       :catalogs
       (mapcat :tagories)
       (mapcat :items)
       (vec)))

(defn add-to-cart [new-item]
  (swap! app/state update-in [:cart]
         (fn [cart]
           (let [[index line-item]
                 (first (filter (fn [[index {:keys [item quantity] :as line-item}]]
                                  (= (:item/id item)
                                     (:item/id new-item)))
                                (map-indexed vector cart)))]
             (if (seq line-item)
               (update-in cart [index] (fn [line-item]
                                         (update-in line-item [:quantity] inc)))
               (vec (conj cart {:item new-item
                                :quantity 1})))))))

(defn remove-from-cart [item]
  (swap! app/state update :cart
         (fn [cart]
           (vec (remove #(= (:item/id (:item %)) (:item/id item))
                        cart)))))

(defn items-grid [display-items]
  [:div.card-grid
   (for [item display-items]
     [:div.card {:key (:item/id item)
                 :data-item-id (str (:id item))
                 :style {:display "flex"
                         :flex-direction "column"
                         :height "450px"}} ; Fixed height for all cards
      [:img {:src (-> item :item/images first :image/url)
             :alt (:item/name item)
             :style {:width "100%"
                     :height "200px"
                     :object-fit "cover"
                     :flex-shrink 0}}] ; Image won't shrink
      [:div.card-content
       {:style {:display "flex"
                :flex-direction "column"
                :flex-grow 1
                :padding "16px"}}
       [:h3 {:style {:margin "0 0 8px 0"}} (:item/name item)]
       [:p {:style {:margin "0 0 auto 0" ; Push everything below down
                    :overflow "hidden"
                    :display "-webkit-box"
                    :-webkit-line-clamp 4 ; Limit to 4 lines
                    :-webkit-box-orient "vertical"
                    :text-overflow "ellipsis"
                    :flex-grow 1}}
        (:item/description item)]

       ;; Price and button always at bottom
       [:div {:style {:margin-top "auto"}} ; This pushes to bottom
        [:div.price
         {:style {:font-weight :bold :color "#e91e63" :font-size "1.2em"
                  :margin-bottom "10px"}}
         "$" (gstring/format "%.2f" (/ (:item/price item) 100.0))]
        [:button.add-to-cart-btn
         {:style {:background "#e91e63"
                  :color "white"
                  :border "none"
                  :border-radius "5px"
                  :padding "8px 16px"
                  :cursor "pointer"
                  :width "100%"}
          :on-click #(add-to-cart item)}
         "Add to Cart"]]]])])
(comment
  (-> @app/state :cart)
  (-> @app/state :cart type)
  (-> @app/state :cart count))

(defn cart-icon [num]
  [:span {:style {:position "relative" :display "inline-block"}}
   [:svg {:xmlns "http://www.w3.org/2000/svg"
          :width "24" :height "24" :viewBox "0 0 24 24" :fill "none" :stroke "currentColor" :stroke-width "2" :stroke-linecap "round" :stroke-linejoin "round" :style {:vertical-align "middle" :margin-right "4px"}}
    [:circle {:cx "9" :cy "21" :r "1"}]
    [:circle {:cx "20" :cy "21" :r "1"}]
    [:path {:d "M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"}]]
   (when (and num (pos? num))
     [:span {:style {:position "absolute"
                     :top "-4px"
                     :right "-4px"
                     :background "#e91e63"
                     :color "white"
                     :border-radius "50%"
                     :padding "0 3px"
                     :font-size "9px"
                     :font-weight "bold"
                     :min-width "12px"
                     :text-align "center"
                     :line-height "1.2"
                     :z-index 1}}
      num])])

(defn submit-cart
  "submit cart to backend to create an order. tx is the algorand transaction information"
  [cart tx]
  (let [store-id (-> @app/state :store :store/id)
        account-id (-> @app/state :account :account/id)
        order-data {:store-id store-id
                    :account-id account-id
                    :cart cart
                    :tx tx}]
    (async/go
      (try
        (let [response (<! (rpc/invoke-with-response 'create-order order-data))]
          (pprint {:create-order-response response})
          (when (= (:type response) :success)
            ;; Clear the cart after successful order creation
            (swap! app/state assoc :cart [])))
        (catch js/Error e
          (pprint {:error "Error submitting order" :exception e}))))))
;; Cart content with pay button and QR code
(defn cart-content [cart]
  (let [payment-status (r/cursor app/state [:payment-status])
        monitor-chan (r/cursor app/state [:monitor-chan])
        store-id (-> @app/state :store :store/id)
        store-name (-> @app/state :store :store/name)
        order-num (r/cursor app/state [:order-num])]
    (fn [cart]
      (when (and store-id (not @order-num))
        (reset! order-num
                (str (subs (str store-id) 0 8) "-" (quot (.now js/Date) 1000))))
      (prn {:cart-content-order-num @order-num})
      (let [algo-address "F7YGGVYNO6NIUZ35UTQQ7GMQPUOELTERYHGGLESYSABC6E5P2ZYMRJPWOQ"
            sub-total (reduce + (map (fn [{:keys [item quantity]}]
                                       (* quantity (:item/price item)))
                                     @cart))
            algo-url (gstring/format "algorand://%s?amount=%s&note=%s" algo-address sub-total (js/encodeURIComponent @order-num))]
        [:div.cart-content {:style {:background-color :white
                                    :width "80%"
                                    :max-width "600px"
                                    :margin "20px auto"
                                    :padding "24px"
                                    :border-radius "8px"
                                    :box-shadow "0 2px 8px rgba(0,0,0,0.08)"}}
         [:h2 {:style {:margin-bottom "16px"}} "Your Cart"]
         (if (seq @cart)
           [:table {:style {:width "100%" :border-collapse "collapse"}}
            [:thead
             [:tr
              [:th {:style {:text-align "left" :padding-bottom "8px"}} "Item"]
              [:th {:style {:text-align "right" :padding-bottom "8px"}} "Subtotal"]
              [:th {:style {:width "30px"}}]]]
            [:tbody
             (doall
              (for [{:keys [item quantity]} @cart]
                ^{:key (str (:item/id item))}
                [:tr
                 [:td {:style {:padding "8px 0"}}
                  [:div {:style {:font-weight "bold"}} (:item/name item)]
                  [:div {:style {:font-size "0.95em" :color "#555"}}
                   (str quantity " × $" (gstring/format "%.2f" (/ (:item/price item) 100.0)))]]
                 [:td {:style {:text-align "right" :font-weight "bold"}}
                  (str "$" (gstring/format "%.2f" (/ (* quantity (:item/price item)) 100.0)))]
                 [:td {:style {:text-align "center"}}
                  [:button {:on-click #(remove-from-cart item)
                            :style {:background "none" :border "none" :color "#ff4444"
                                    :cursor "pointer" :font-size "1.2em"}}
                   "×"]]]))]]
           [:div {:style {:color "#888" :padding "32px" :text-align "center"}} "Your cart is empty."])
         (when (seq @cart)
           [:div {:style {:marginTop "24px" :textAlign "right" :fontWeight "bold" :fontSize "1.2em"}}
            "Subtotal: " [:span {:style {:color "#e91e63"}} (str "$" (gstring/format "%.2f" (/ sub-total 100.0)))]
            (case @payment-status
              :pending
              [:div {:style {:textAlign "center"}}
               [:a {:href algo-url
                    :on-click (fn []
                                (reset! payment-status :monitoring)
                                (let [monitor (wallet/monitor-transactions
                                               algo-address
                                               (fn [txs]
                                                 (let [tx (first txs)
                                                       tx-order-num (:note-decoded tx)
                                                       receiver (-> tx :payment-transaction :receiver)]
                                                   (when (and (= tx-order-num @order-num)
                                                              (= receiver algo-address))
                                                     (reset! payment-status :confirmed)
                                                     (reset! order-num nil)
                                                     (submit-cart @cart tx)
                                                     (when-let [stop-ch @monitor-chan]
                                                       (async/close! stop-ch)
                                                       (reset! monitor-chan nil)))))
                                               {:interval-ms 5000})]
                                  (reset! monitor-chan (:stop monitor))))
                    :style {:display "inline-block"
                            :background "#e91e63"
                            :color "white"
                            :border "none"
                            :border-radius "5px"
                            :padding "10px 20px"
                            :cursor "pointer"
                            :font-size "1.1em"
                            :margin-top "16px"
                            :text-decoration "none"}}
                "Pay with Algo"]]
              :monitoring
              [:div {:style {:marginTop "16px" :textAlign "center"}}
               [:div {:style {:marginBottom "8px" :fontWeight "bold" :fontSize "1.1em" :color "#333"}}
                "Send payment to:"]
               [:div {:style {:marginBottom "8px" :fontFamily "monospace" :fontSize "1em" :wordBreak "break-all"}}
                algo-address]
               [wallet/generate-payment-qr-code algo-url]
               [:div {:style {:marginTop "12px" :color "#555"}}
                "Waiting for payment..."]
               [:button {:on-click (fn []
                                     (reset! payment-status :pending)
                                     (when-let [stop-ch @monitor-chan]
                                       (async/close! stop-ch)
                                       (reset! monitor-chan nil)))
                         :style {:background "#aaa"
                                 :color "white"
                                 :border "none"
                                 :border-radius "5px"
                                 :padding "8px 16px"
                                 :cursor "pointer"
                                 :margin-top "10px"}} "Cancel Payment"]]
              :confirmed
              [:div {:style {:marginTop "24px"
                             :padding "20px"
                             :background "#e8f5e9"
                             :border-left "5px solid #4caf50"
                             :textAlign "center"
                             :color "#2e7d32"
                             :font-size "1.1em"}}
               "Thank you! Your payment has been confirmed."])])]))))

(defn hide-menu! []
  (when-let [nav-el (.querySelector js/document ".navigation.active")]
    (.remove (.-classList nav-el) "active")))

(defn create-tab [{:keys [id class content on-click]}]
  [:div.tab {:key id
             :data-tagory-id id
             :class class
             :on-click on-click}
   content])

(defn contact-page []
  [:div {:style {:padding "20px" :background-color "white" :margin "20px auto"
                 :border-radius "8px" :text-align "center" :max-width "600px"}}
   [:h2 {:style {:margin-bottom "16px"}} "Contact Us"]
   [:p {:style {:margin "8px 0"}} "For any inquiries, please reach out to us at "
    [:a {:href "mailto:tt@ttgamestock.com"} "tt@ttgamestock.com"] "."]
   [:p {:style {:margin "8px 0"}} "WhatsApp: " [:a {:href "https://wa.me/8613928458941" :target "_blank" :rel "noopener noreferrer"} "+86 139 2845 8941"]]])

(defn tabs [state]
  (let [active-tab (r/atom :all-products)]
    (fn [state]
      (let [tagories (get-in @state [:store :catalogs 0 :tagories])
            tagories-tab (mapv (fn [tagory]
                                 (let [id (:tagory/id tagory)]
                                   (create-tab {:id id
                                                :content (:tagory/name tagory)
                                                :class (if (= @active-tab id)
                                                         :active)
                                                :on-click #(do
                                                             (hide-menu!)
                                                             (reset! active-tab id)
                                                             (swap! app/state assoc
                                                                    :content (items-grid (:items tagory))))})))
                               tagories)
            all-tabs (if (> (count tagories) 1)
                       (let [all-products-tab (create-tab {:id :all-products
                                                           :content "All Products"
                                                           :class (if (= @active-tab :all-products)
                                                                    :active)
                                                           :on-click #(do
                                                                        (reset! active-tab :all-products)
                                                                        (hide-menu!)
                                                                        (swap! app/state assoc
                                                                               :content (items-grid
                                                                                         (get-all-items (:store @app/state)))))})]
                         (concat [all-products-tab]
                                 tagories-tab))
                       tagories-tab)
            cart-count (reduce + (map :quantity (:cart @app/state)))
            cart-tab (create-tab {:id :cart
                                  :content (cart-icon cart-count)
                                  :class (if (= @active-tab :cart)
                                           :active)
                                  :on-click #(do
                                               (reset! active-tab :cart)
                                               (hide-menu!)
                                               (swap! app/state assoc :content [cart-content (r/cursor state [:cart])]))})
            contact-tab (create-tab {:id :contact
                                     :content "Contact"
                                     :class (if (= @active-tab :contact)
                                              :active)
                                     :on-click #(do
                                                  (hide-menu!)
                                                  (reset! active-tab :contact)
                                                  (swap! app/state assoc :content [contact-page]))})
            account-tab (create-tab {:id :pera-wallet
                                     :content "Account"
                                     :class (if (= @active-tab :account)
                                              :active)
                                     :on-click #(do
                                                  (hide-menu!)
                                                  (reset! active-tab :account)
                                                  (swap! app/state assoc :content [wallet/pera-wallet-connect-component]))})
            all-tabs (concat all-tabs [cart-tab account-tab contact-tab])]
        [:div.tab-bar
         (when (seq all-tabs)
           all-tabs)]))))

(defn toggle-menu []
  (.. js/document (querySelector ".navigation") -classList (toggle "active")))

(defn header [state]
  [:div.header-container
   [:div.search-container
    [:input.search-box {:type "text" :placeholder "Search cosmetics..."}]]
   [:button.menu-toggle {:on-click toggle-menu}
    [:span.hamburger]]
   [:nav.navigation
    [tabs state]]])

(defn main-ui [state]
  [:div
   [header state]
   (:content @state)])

(defonce root (atom nil))

(defn mount-main-ui [state]
  (when-let [container (.getElementById js/document "app")]
    (when-not @root
      (reset! root (rdc/create-root container)))

    (swap! app/state assoc
           :context-menu {:visible? false :x 0 :y 0}
           :payment-status :pending
           :monitor-chan nil
           :grid {:rows []
                  :columns []
                  :selected-rows (sorted-set)
                  :sort-col nil
                  :sort-dir :asc
                  :dirty-cells #{}})

    (rdc/render @root [main-ui state])))

(defn load-store [tenant-name store-name]
  (async/go
    (try
      (let [store (<! (rpc/invoke-with-response 'get-store tenant-name store-name))]
        (when store
          (let [all-items (get-all-items store)]
            (swap! app/state assoc
                   :store store
                   :content (items-grid all-items)))))
      (catch js/Error e
        (pprint {:error "Error loading store" :exception e})))))

(defn init! []
  (set-view-from-fragment)
  (mount-main-ui app/state)

  (let [wss (stream/map->WebSocketStream {:url "/wsstream"})
        wss (stream/open wss {})]
    (swap! app/state assoc :wss wss)
    (rpc/start-response-handler wss)

    (async/go
      (loop []
        (when (not= (:status (stream/status wss)) :connected)
          (<! (async/timeout 100))
          (recur)))
      (load-store "TT Cosmetics" "TT Cosmetics Downtown NYC"))))

(comment
  (-> @app/state keys)

  {:submit-cart
   [{:item
     {:item/id #uuid "550e8400-e29b-41d4-a716-446655440010",
      :item/name "Matte Liquid Lipstick - Ruby Red",
      :item/description
      "Long-lasting matte liquid lipstick with rich, vibrant color that stays put all day. Comfortable, non-drying formula.",
      :item/price 1,
      :item/images
      [{:image/id #uuid "550e8400-e29b-41d4-a716-446655440100",
        :image/url
        "https://images.unsplash.com/photo-1586495777744-4413f21062fa?w=400&h=400&fit=crop&crop=center",
        :image/alt "Matte Liquid Lipstick Ruby Red"}
       {:image/id #uuid "550e8400-e29b-41d4-a716-446655440101",
        :image/url
        "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?w=400&h=400&fit=crop&crop=center",
        :image/alt "Lipstick application close-up"}]},
     :quantity 1}],
   :tx
   {:fee 1000,
    :genesis-hash "wGHE2Pwdvd7S12BL5FaOP20EGYesN73ktiC1qzkkit8=",
    :sender-rewards 0,
    :closing-amount 0,
    :note-decoded "550e8400-1759999919",
    :signature
    {:sig
     "kPykyq4Ux3gmMTZVMpBHgnKEqnKSvAlTGauKR/NWAipZzTDB6xuQy2JleM4Cgmmhm6YXZgzszO86Z12UGBw7Bg=="},
    :tx-type "pay",
    :intra-round-offset 16,
    :payment-transaction
    {:amount 1,
     :close-amount 0,
     :receiver
     "F7YGGVYNO6NIUZ35UTQQ7GMQPUOELTERYHGGLESYSABC6E5P2ZYMRJPWOQ"},
    :confirmed-round 54439014,
    :note "NTUwZTg0MDAtMTc1OTk5OTkxOQ==",
    :receiver-rewards 0,
    :round-time 1759999962,
    :last-valid 54440012,
    :close-rewards 0,
    :id "TQ2MXR6VNC4ZYOIZCWR3YD2ORVWTHAW3PU3PC46ZJSRMUPQV3RCQ",
    :genesis-id "mainnet-v1.0",
    :sender "4BJZEDCUP5S6IILRDCXJJFE6VDGA5GRPF4WRXQSG7RDPHASOQCT4IO3DZE",
    :first-valid 54439012}})

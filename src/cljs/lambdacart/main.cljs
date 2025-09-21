(ns lambdacart.main
  (:require [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]
            [lambdacart.app :as app]
            [lambdacart.rpc :as rpc]
            [lambdacart.stream :as stream]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [goog.string :as gstring]
            [goog.string.format]))

(defn get-all-items [tenant-data]
  "Get all items from all catalogs and tagories"
  (->> (:catalogs tenant-data)
       (mapcat :tagories)
       (mapcat :items)
       (vec)))

(defn add-to-cart [item]
  "Add item to shopping cart"
  (js/console.log "Added to cart:" (:item/name item))
  (js/alert (str "Added " (:item/name item) " to cart!")))

(defn tabs [state]
  (let [tagories (get-in @state [:catalogs 0 :tagories])
        active-tagory (:active-tagory @state)]
    [:div
     (for [tagory tagories
           :let [id (:tagory/id tagory)]]
       [:button.tab
        {:key id
         :data-tagory-id id
         :class (when (= active-tagory tagory)
                  "active")
         :on-click #(let [active-tagory tagory]
                      (swap! app/state assoc
                             :active-tagory active-tagory
                             :display-items (:items tagory)))}
        (:tagory/name tagory)])]))

(defn main-ui [state]
  [:div
   [:div.header-container
    [:div.search-container
     [:input.search-box {:type "text" :placeholder "Search cosmetics..."}]]
    [:button.menu-toggle {:onclick "lambdacart.cosmetics.toggleMenu()"}
     [:span.hamburger]]
    [:nav.navigation
     (let [active-tagory (:active-tagory @state)]
       [:div.tab-bar
        [:button.tab {:class (if (or (nil? active-tagory)
                                     (= active-tagory {:tagory/name :all}))
                               "active")
                      :on-click #(let [all-items (get-all-items @state)]
                                   (swap! app/state assoc
                                          :active-tagory nil
                                          :display-items all-items))}
         "All Products"]
        [tabs state]])]]

   [:div.card-grid
    (for [item (:display-items @state)]
      [:div.card {:key (:item/id item)
                  :data-item-id (str (:id item))
                  :style {:display "flex"
                          :flex-direction "column"
                          :height "450px"}}  ; Fixed height for all cards
       [:img {:src (-> item :item/images first :image/url)
              :alt (:item/name item)
              :style {:width "100%" 
                      :height "200px" 
                      :object-fit "cover"
                      :flex-shrink 0}}]  ; Image won't shrink
       [:div.card-content
        {:style {:display "flex"
                 :flex-direction "column"
                 :flex-grow 1
                 :padding "16px"}}
        [:h3 {:style {:margin "0 0 8px 0"}} (:item/name item)]
        [:p {:style {:margin "0 0 auto 0"  ; Push everything below down
                     :overflow "hidden"
                     :display "-webkit-box"
                     :-webkit-line-clamp 4  ; Limit to 4 lines
                     :-webkit-box-orient "vertical"
                     :text-overflow "ellipsis"
                     :flex-grow 1}} 
         (:item/description item)]
        
        ;; Price and button always at bottom
        [:div {:style {:margin-top "auto"}}  ; This pushes to bottom
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
          "Add to Cart"]]]])]])

(defonce root (atom nil))

(defn mount-main-ui [state]
  (when-let [container (.getElementById js/document "app")]
    (when-not @root
      (reset! root (rdc/create-root container)))

    (swap! app/state assoc
           :context-menu {:visible? false :x 0 :y 0}
           :grid {:rows []
                  :columns []
                  :selected-rows (sorted-set)
                  :sort-col nil
                  :sort-dir :asc
                  :dirty-cells #{}})

    (rdc/render @root [main-ui state])))

(defn load-tenant [tenant-name]
  (async/go
    (try
      (js/console.log "Loading tenant:" tenant-name)

      ;; Call the server-side get-tenant function via RPC
      (let [response (<! (rpc/invoke-with-response 'get-tenant tenant-name))]

        (js/console.log "Server response:" (clj->js response))
        (when response
          ;; Get all items using the reusable function
          (let [all-items (get-all-items response)]

            ;; Reset state with response data and initialize display-items
            (reset! app/state (assoc response :display-items all-items))

            (js/console.log "Loaded" (count all-items) "items total"))))
      (catch js/Error e
        (js/console.error "Error loading tenant:" e)
        (js/alert (str "Error loading tenant: " (.-message e)))))))

(defn init! []
  (mount-main-ui app/state)

  (let [wss (stream/map->WebSocketStream {:url "/wsstream"})
        wss (stream/open wss {})]
    (swap! app/state assoc :wss wss)
    (rpc/start-response-handler wss)

    ;; Wait for WebSocket to be ready, then load data
    (async/go
      (js/console.log "Waiting for WebSocket connection...")
      ;; Wait for the WebSocket to be in OPEN state
      (loop [attempts 0]
        (if (and (< attempts 50) ; Max 5 seconds
                 (not= (.-readyState (:ws wss)) 1)) ; 1 = OPEN
          (do
            (<! (async/timeout 100))
            (recur (inc attempts)))
          (if (= (.-readyState (:ws wss)) 1)
            (do
              (js/console.log "WebSocket connected, loading data...")
              (load-tenant "TT Cosmetics"))
            (js/console.error "WebSocket failed to connect after 5 seconds")))))))

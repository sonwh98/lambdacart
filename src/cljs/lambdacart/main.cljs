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
    (swap! app/state assoc :view (keyword fragment))
    (pprint {:fragment fragment})))

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

(defn add-to-cart [item]
  "Add item to shopping cart"
  (pprint {:action "Added to cart" :item-name (:item/name item)})
  (js/alert (str "Added " (:item/name item) " to cart!")))

(defn tabs [state]
  (let [tagories (get-in @state [:store :catalogs 0 :tagories])
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

(defn items-grid [display-items]
  [:div.card-grid
   (for [item @display-items]
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

(defn toggle-menu []
  (.. js/document (querySelector ".navigation") -classList (toggle "active")))

(defn header [state]
  [:div.header-container
   [:div.search-container
    [:input.search-box {:type "text" :placeholder "Search cosmetics..."}]]
   [:button.menu-toggle {:on-click toggle-menu}
    [:span.hamburger]]
   [:nav.navigation
    (let [active-tagory (:active-tagory @state)]
      [:div.tab-bar
       [:button.tab {:class (if (or (nil? active-tagory)
                                    (= active-tagory {:tagory/name :all}))
                              "active")
                     :on-click #(let [all-items (get-all-items (:store @state))]
                                  (swap! state assoc
                                         :active-tagory nil
                                         :display-items all-items))}
        "All Products"]
       [tabs state]])]])

(defn main-ui [state]
  [:div
   (let [view (:view @state)]
     (case view
       :grid
       (let [grid-state (r/cursor app/state [:grid])
             context-menu-state (r/cursor app/state [:context-menu])]
         [:div {:style {:background-color :white}}
          [grid/grid-component grid-state context-menu-state]
          [grid/context-menu-component grid-state context-menu-state]])
       :wallet
       [wallet/wallet-component]
       ;;default
       [:div
        [header state]
        [items-grid (r/cursor state [:display-items])]]))])

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

(defn load-store [tenant-name store-name]
  (async/go
    (try
      (pprint {:action "Loading store" :store-name store-name :tenant tenant-name})
      (let [store (<! (rpc/invoke-with-response 'get-store tenant-name store-name))]
        (when store
          (let [all-items (get-all-items store)]
            (swap! app/state assoc
                   :store store
                   :display-items all-items))))
      (catch js/Error e
        (pprint {:error "Error loading store" :exception e})))))

(defn init! []
  (set-view-from-fragment)
  (mount-main-ui app/state)

  (let [wss (stream/map->WebSocketStream {:url "/wsstream"})
        wss (stream/open wss {})]
    (swap! app/state assoc :wss wss)
    (rpc/start-response-handler wss)

    ;; Wait for WebSocket to be ready, then load data
    (async/go
      (pprint {:status "Waiting for WebSocket connection..."})
      ;; Wait for the WebSocket to be in OPEN state
      (loop [attempts 0]
        (if (and (< attempts 50) ; Max 5 seconds
                 (not= (.-readyState (:ws wss)) 1)) ; 1 = OPEN
          (do
            (<! (async/timeout 100))
            (recur (inc attempts)))
          (if (= (.-readyState (:ws wss)) 1)
            (do
              (pprint {:status "WebSocket connected, loading data..."})
              (load-store "TT Cosmetics" "TT Cosmetics Downtown NYC")
              (grid/load-and-display-data))
            (pprint {:error "WebSocket failed to connect after 5 seconds"})))))))

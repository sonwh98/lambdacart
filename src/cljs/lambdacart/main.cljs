(ns lambdacart.main
  (:require [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]
            [lambdacart.app :as app]
            [lambdacart.rpc :as rpc]
            [lambdacart.stream :as stream]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]))

(defn main-ui [state]
  [:html {:lang "en"}
   [:head
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
    [:title "TT Cosmetics"]
    [:link {:rel "stylesheet" :href "/css/style.css"}]
    [:script {:src "/js/cosmetics.js"}]]
   [:body
    [:div.header-container
     [:div.search-container
      [:input.search-box {:type "text" :placeholder "Search cosmetics..."}]]
     [:button.menu-toggle {:onclick "lambdacart.cosmetics.toggleMenu()"}
      [:span.hamburger]]
     [:nav.navigation
      [:div.tab-bar
       [:button.tab {:class "active"} "All Products"]
       ;; Dynamically generate tagory tabs
       #_(for [tagory tagories]
           [:button.tab 
            {:data-tagory-id (str (:id tagory))
             :onclick (str "lambdacart.cosmetics.filterByTagory('" (:id tagory) "')")}
            (:name tagory)])]]]

    [:div.card-grid
     #_(for [item items]
         [:div.card {:data-item-id (str (:id item))}
          [:img {:src (:image-url item)
                 :alt (:name item)
                 :style {:width "100%" :height 200 :object-fit :cover}}]
          [:div.card-content
           [:h3 (:name item)]
           [:p (:description item)]
           [:div.price 
            {:style {:font-weight :bold :color "#e91e63" :font-size "1.2em"}}
            "$" (format "%.2f" (/ (:price item) 100.0))]]])]]]
  )

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
        (if (and (< attempts 50)                    ; Max 5 seconds
                 (not= (.-readyState (:ws wss)) 1)) ; 1 = OPEN
          (do
            (<! (async/timeout 100))
            (recur (inc attempts)))
          (if (= (.-readyState (:ws wss)) 1)
            (do
              (js/console.log "WebSocket connected, loading data...")
              )
            (js/console.error "WebSocket failed to connect after 5 seconds")))))))

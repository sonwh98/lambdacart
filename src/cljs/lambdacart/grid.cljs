(ns lambdacart.grid
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [lambdacart.app :as app]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]))

;; Add context menu state
(def context-menu (r/atom {:visible? false :x 0 :y 0}))

;; Add delete function
(defn delete-selected-rows [grid-state]
  (let [selected (-> @grid-state :selected-rows)
        rows (-> @grid-state :rows)]
    (swap! grid-state assoc :rows 
           (vec (keep-indexed #(when-not (selected %1) %2) rows)))
    (swap! grid-state assoc :selected-rows (sorted-set))
    (reset! context-menu {:visible? false :x 0 :y 0})))

;; Add context menu component
(defn context-menu-component [grid-state]
  (when (:visible? @context-menu)
    [:div {:style {:position "fixed"
                   :left (str (:x @context-menu) "px")
                   :top (str (:y @context-menu) "px")
                   :background "white"
                   :border "1px solid #ccc"
                   :box-shadow "2px 2px 5px rgba(0,0,0,0.2)"
                   :z-index 1000}}
     [:div {:style {:padding "8px 12px"
                    :cursor "pointer"
                    :hover {:background "#f5f5f5"}}
            :on-click #(delete-selected-rows grid-state)}
      "Delete Selected Rows"]]))

(defn header [grid-state]
  [:div {:style {:display "flex"
                 :position "sticky"
                 :top "0"
                 :cursor "pointer"
                 :background "#f0f0f0"
                 :z-index "1"
                 :font-weight "bold"
                 :border-bottom "1px solid #ccc"}}
   
   [:div {:style {:width "20px" :height "20px" :padding "10px" :border-right "1px solid #ddd"}} ""]
   (let [header (-> @grid-state :header)
         style {:flex "1" :padding "10px" :border-right "1px solid #ddd"}]
     (doall (for [[i h] (map-indexed (fn [i h]
                                       [i h])
                                     header)]
              [:div {:key (str "h-" h)
                     :style style
                     :on-click (fn [evt]
                                 (let [rows (-> @grid-state :rows)
                                       sorted-rows (vec (sort-by (fn [row]
                                                                   (nth row i))
                                                                 rows))]
                                   (swap! grid-state assoc-in [:rows]
                                          sorted-rows)))} h])))])

(defn handle-key-nav [grid-state current-idx current-row e]
  (let [key->direction {"ArrowLeft" [-1 0]
                       "ArrowRight" [1 0]
                       "ArrowUp" [0 -1]
                       "ArrowDown" [0 1]}
        [dx dy] (get key->direction (.-key e))
        rows (-> @grid-state :rows)
        num-cols (-> rows first count)]
    (when (and dx dy)
      (.preventDefault e)
      (let [next-idx (+ current-idx dx)
            next-row (+ current-row dy)
            next-el (when (and (>= next-idx 0) 
                             (< next-idx num-cols)
                             (>= next-row 0)
                             (< next-row (count rows)))
                     (.querySelector js/document 
                                   (str "[data-row='" next-row "'][data-col='" next-idx "']")))]
        (when next-el
          (.focus next-el))))))

(defn update-cell [grid-state row-idx col-idx value]
  (swap! grid-state assoc-in [:rows row-idx col-idx] value))

(defn cell-component [grid-state row-idx col-idx]
  (let [value (get-in @grid-state [:rows row-idx col-idx])]
    [:input {:type "text"
             :value value
             :data-row row-idx
             :data-col col-idx
             :style {:flex "1" 
                    :padding "8px" 
                    :border "none"
                    :background :inherit
                    :border-bottom "1px solid #eee" 
                    :border-right "1px solid #f9f9f9"}
             :on-focus #(when (and (seq (:selected-rows @grid-state))
                                  (not ((:selected-rows @grid-state) row-idx)))
                         (swap! grid-state dissoc :selected-rows))
             :on-key-down #(handle-key-nav grid-state col-idx row-idx %)
             :on-change #(update-cell grid-state row-idx col-idx (.. % -target -value))}]))

;; Modify grid-component to include context menu
(defn grid-component [grid-state]
  (let [rows (-> @grid-state :rows)]
    [:div {:on-click #(when (:visible? @context-menu)
                       (reset! context-menu {:visible? false :x 0 :y 0}))
           :on-context-menu #(.preventDefault %)}
     [header grid-state]
     [context-menu-component grid-state]  ; Add context menu
     [:div {:style {:width "100%"
                    :height "90%"
                    :border "1px solid #ccc"
                    :overflow "auto"
                    :font-family "sans-serif"
                    :font-size "14px"
                    :position "relative"}}
      (doall 
       (for [[i _row] (map-indexed vector rows)]
         [:div {:style {:display "flex"
                        :background (when (contains? (:selected-rows @grid-state) i)
                                    "#e8f2ff")}
                :key i
                :on-context-menu (fn [e]
                                  (.preventDefault e)
                                  (when (contains? (:selected-rows @grid-state) i)
                                    (reset! context-menu 
                                           {:visible? true
                                            :x (.-clientX e)
                                            :y (.-clientY e)})))}
          [:div {:style {:width "20px" 
                        :height "20px" 
                        :padding "10px"
                        :cursor "pointer"
                        :border-right "1px solid #ddd"
                        :background "#f0f0f0"
                        :font-weight "bold"}
                 :on-click #(swap! grid-state update :selected-rows
                                 (fn [selected]
                                   (if (contains? selected i)
                                     (disj selected i)
                                     (conj (or selected (sorted-set)) i))))}
           (inc i)]
          [:<>
           [cell-component grid-state i 0]
           [cell-component grid-state i 1]
           [cell-component grid-state i 2]]]))]]))

(defonce root (atom nil))

(defn mount-grid []
  (when-let [container (.getElementById js/document "app")]
    (when-not @root
      (reset! root (rdc/create-root container))
      (swap! app/state assoc-in [:grid :rows] 
             (vec (for [i (range 50)]
                    (mapv str [(rand-int 100) (rand-int 100) (rand-int 100)]))))
      (swap! app/state assoc-in [:grid :header] ["Tour Name" "Description" "Image"])
      (swap! app/state assoc-in [:grid :selected-rows] (sorted-set)))
    (rdc/render @root [grid-component (r/cursor app/state [:grid])])))

(defn open-websocket [url]
  (let [ws (js/WebSocket. url)
        ch (chan)]
    
    (set! (.-onopen ws)
          (fn [_]
            (js/console.log "WebSocket connection opened")))
    
    (set! (.-onmessage ws)
          (fn [event]
            (put! ch (.-data event))))
    
    (set! (.-onclose ws)
          (fn [_]
            (js/console.log "WebSocket connection closed")
            (close! ch)))
    
    (set! (.-onerror ws)
          (fn [error]
            (js/console.error "WebSocket error:" error)))
    
    ;; Handle outgoing messages
    (async/go-loop []
      (when-let [msg (<! ch)]
        (.send ws msg)
        (recur)))
    
    ch))

(defn init! []
  (mount-grid)
  (let [ws-channel (open-websocket "ws://localhost:3002")]
    (swap! app/state assoc :ws-channel ws-channel)))



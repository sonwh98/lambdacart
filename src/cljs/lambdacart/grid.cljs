(ns lambdacart.grid
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [lambdacart.app :as app]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]))

(defn delete-selected-rows [grid-state context-menu]
  (let [selected (-> @grid-state :selected-rows)
        rows (-> @grid-state :rows)]
    (swap! grid-state assoc :rows
           (vec (keep-indexed #(when-not (selected %1) %2) rows)))
    (swap! grid-state assoc :selected-rows (sorted-set))
    (reset! context-menu {:visible? false :x 0 :y 0})))

(defn context-menu-component [grid-state context-menu]
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
            :on-click #(delete-selected-rows grid-state context-menu)}
      "Delete"]]))

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
         sort-col (-> @grid-state :sort-col)
         sort-dir (-> @grid-state :sort-dir)
         style {:flex "1" :padding "10px" :border-right "1px solid #ddd"}]
     (doall
      (for [[i h] (map-indexed vector header)]
        [:div {:key (str "h-" h)
               :style (merge style
                             {:display "flex"
                              :justify-content "space-between"
                              :align-items "center"})
               :on-click (fn [_]
                           (let [rows (-> @grid-state :rows)
                                 new-dir (if (= i sort-col)
                                           (if (= sort-dir :asc) :desc :asc)
                                           :asc)
                                 sorted-rows (vec (sort-by #(nth % i)
                                                           (if (= new-dir :desc) #(compare %2 %1) compare)
                                                           rows))]
                             (swap! grid-state assoc
                                    :rows sorted-rows
                                    :sort-col i
                                    :sort-dir new-dir)))}
         [:span h]
         (when (= i sort-col)
           [:span {:style {:margin-left "8px"}}
            (if (= sort-dir :asc) "▲" "▼")])])))])

(defn handle-key-nav [rows-state current-idx current-row e]
  (let [key->direction {"ArrowLeft" [-1 0]
                        "ArrowRight" [1 0]
                        "ArrowUp" [0 -1]
                        "ArrowDown" [0 1]}
        [dx dy] (get key->direction (.-key e))
        num-of-cols (-> @rows-state first count)
        num-of-rows (count @rows-state)]
    (when (and dx dy)
      (.preventDefault e)
      (let [next-idx (+ current-idx dx)
            next-row (+ current-row dy)
            next-el (when (and (>= next-idx 0)
                               (< next-idx num-of-cols)
                               (>= next-row 0)
                               (< next-row num-of-rows))
                      (.querySelector js/document
                                      (str "[data-row='" next-row "'][data-col='" next-idx "']")))]
        (when next-el
          (.focus next-el))))))

(defn update-cell [grid-state row-idx col-idx value]
  (swap! grid-state assoc-in [:rows row-idx col-idx] value))

(defn cell-component [grid-state row-idx col-idx]
  (let [value (get-in @grid-state [:rows row-idx col-idx])
        rows (r/cursor grid-state [:rows])]
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
             :on-key-down #(handle-key-nav rows col-idx row-idx %)
             :on-change #(update-cell grid-state row-idx col-idx (.. % -target -value))}]))

(defn grid-component [grid-state]
  (prn :grid-component)
  (let [rows (-> @grid-state :rows)
        context-menu (r/cursor app/state [:context-menu])]
    [:div {:on-click #(when (:visible? @context-menu)
                        (swap! context-menu assoc :visible? false))
           :on-context-menu #(.preventDefault %)}
     [header grid-state]
     [context-menu-component grid-state context-menu]
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
      (swap! app/state assoc :grid
             {:rows (vec (for [i (range 50)]
                           (mapv str [(rand-int 100) (rand-int 100) (rand-int 100)])))
              :header ["Tour Name" "Description" "Image"]
              :selected-rows (sorted-set)
              :sort-col nil
              :sort-dir :asc})
      (swap! app/state assoc :context-menu {:visible? false :x 0 :y 0}))
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



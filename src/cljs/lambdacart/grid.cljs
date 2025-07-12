(ns lambdacart.grid
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [lambdacart.app :as app]
            [cljs.core.async :refer [chan put! <! >! close! timeout] :as async]))

(defprotocol Stream
  (open [this params] "Open the stream with param which has only 1 mandatory key :path to a resource in a graph")
  (read [this params] "Read from the stream with params")
  (write [this data params] "Write data to the stream with")
  (close [this] "Close the stream."))

(defrecord WebSocketStream [url ws in out]
  Stream
  (open [this _]
    (let [full-url (if (re-matches #"^wss?://.*" url)
                     url  ; Already has protocol
                     (let [protocol (if (= (.-protocol js/location) "https:")
                                      "wss:"
                                      "ws:")
                           host (.-host js/location)]
                       (str protocol "//" host url)))
          ws (js/WebSocket. full-url)
          in (chan 10)
          out (chan 10)]
      (set! (.-onopen ws)
            (fn [_] (js/console.log "WebSocket connection opened")))
      (set! (.-onmessage ws)
            (fn [event] (put! in (.-data event))))
      (set! (.-onclose ws)
            (fn [_]
              (js/console.log "WebSocket connection closed")
              (close! in)
              (close! out)))
      (set! (.-onerror ws)
            (fn [error]
              (js/console.error "WebSocket error:" error)
              (close! in)
              (close! out)))
      (async/go-loop []
        (when-let [msg (<! out)]
          (when (= (.-readyState ws) 1)
            (.send ws msg))
          (recur)))
      (assoc this :ws ws :in in :out out)))

  (read [this {:keys [as timeout-ms] :or {as :channel}}]
    (let [in-stream (:in this)]
      (case as
        :channel in-stream
        :value (async/go
                 (if timeout-ms
                   (let [[val port] (async/alts! [in-stream (async/timeout timeout-ms)])]
                     (if (= port in-stream)
                       val
                       (do
                         (js/console.log "Stream read timeout after" timeout-ms "ms")
                         ::timeout))) ; Return a keyword to indicate timeout
                   (<! in-stream))))))

  (write [this data params]
    (let [out-stream (:out this)
          {:keys [callback] :or {callback nil}} params]
      (if callback
        (put! out-stream data callback)
        (put! out-stream data))))

  (close [this]
    (when-let [ws (:ws this)] (.close ws))
    (close! (:in this))
    (close! (:out this))))

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
   (let [columns (-> @grid-state :columns)
         sort-col (-> @grid-state :sort-col)
         sort-dir (-> @grid-state :sort-dir)
         style {:flex "1" :padding "10px" :border-right "1px solid #ddd"}]
     (doall
      (for [[i column] (map-indexed vector columns)]
        [:div {:key (str "h-" (:name column))
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
         [:span (:name column)]
         (when (= i sort-col)
           [:span {:style {:margin-left "8px"}}
            (if (= sort-dir :asc) "▲" "▼")])])))])

(defn handle-key-nav [row-idx col-idx e]
  (let [rows (-> @app/state :grid :rows)
        row (nth rows col-idx)
        key->direction {"ArrowLeft" [-1 0]
                        "ArrowRight" [1 0]
                        "ArrowUp" [0 -1]
                        "ArrowDown" [0 1]}
        [dx dy] (get key->direction (.-key e))
        num-of-rows (count rows)
        num-of-cols (count row)]
    (when (and dx dy)
      (.preventDefault e)
      (let [next-idx (+ col-idx dx)
            next-row (+ row-idx dy)
            next-el (when (and (>= next-idx 0)
                               (< next-idx num-of-cols)
                               (>= next-row 0)
                               (< next-row num-of-rows))
                      (.querySelector js/document
                                      (str "[data-row='" next-row "'][data-col='" next-idx "']")))]
        (when next-el
          (.focus next-el))))))

(defn update-cell [row-idx col-idx str-value]
  (let [column-type (get-in @app/state [:grid :columns col-idx :type])
        {:keys [pred from-str]} column-type
        value (from-str str-value)]
    (when (pred value)
      (swap! app/state assoc-in [:grid :rows row-idx col-idx] value))))

(defn cell-component [cell-value row-idx col-idx]
  #_(prn :cell-component cell-value)
  [:input {:type "text"
           :value cell-value
           :data-row row-idx
           :data-col col-idx
           :style {:flex "1"
                   :padding "8px"
                   :border "none"
                   :background :inherit
                   :border-bottom "1px solid #eee"
                   :border-right "1px solid #f9f9f9"}
           :on-focus #(when (and (-> @app/state :grid :selected-rows seq)
                                 (not (= (-> @app/state :grid :selected-rows)
                                         row-idx)))
                        (swap! app/state assoc-in [:grid :selected-rows] nil))
           :on-key-down #(handle-key-nav row-idx col-idx %)
           :on-change #(update-cell row-idx col-idx (.. % -target -value))}])

(defn grid-component [grid-state]
  #_(prn :grid-component)
  (let [rows (-> @grid-state :rows)
        num-of-rows (count rows)
        rows-state (for [i (range num-of-rows)]
                     (r/cursor grid-state [:rows i]))

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
       (for [i (range num-of-rows)
             :let [row-state (nth rows-state i)]]
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
          (doall
           (for [j (range (count @row-state))]
             ^{:key (str "cell-" i "-" j)} [cell-component (nth @row-state j) i j]))]))]]))

(defonce root (atom nil))

(def types {:int {:pred integer?
                  :from-str js/parseInt
                  :to-str str}
            :str {:pred string?
                  :from-str str
                  :to-str str}})

(defn mount-grid []
  (when-let [container (.getElementById js/document "app")]
    (when-not @root
      (reset! root (rdc/create-root container))
      (swap! app/state assoc :grid
             {:rows (vec (for [i (range 50)]
                           [(rand-int 100) (str (rand-int 100)) (rand-int 100)]))
              :columns [{:name "Tour Name" :type (:int types)}
                        {:name "Description" :type (:str types)}
                        {:name "Image" :type (:int types)}]
              :selected-rows (sorted-set)
              :sort-col nil
              :sort-dir :asc})
      (swap! app/state assoc :context-menu {:visible? false :x 0 :y 0}))
    (rdc/render @root [grid-component (r/cursor app/state [:grid])])))

(defn init! []
  (mount-grid)
  (let [wss (map->WebSocketStream {:url "/wsstream"})
        wss (open wss {})]
    (swap! app/state assoc :wss wss)))

(comment
  (-> @app/state :wss)
  (write (-> @app/state :wss) "123" {})
  (write (-> @app/state :wss) ["123" 45 6] {})

  ;; Reading examples:
  ;; Get the raw channel for manual handling
  (read (-> @app/state :wss) {:as :channel})

  ;; Read a single value (returns a go block with the value)
  (def data (read (-> @app/state :wss) {:as :value}))
  (async/take! data
               (fn [msg]
                 (prn "got " msg)))
  
  ;; Read with timeout (returns nil if timeout exceeded)
  (read (-> @app/state :wss) {:as :value :timeout-ms 5000})

  ;; Example of consuming messages in a go block
  (async/go
    (let [message (<! (read (-> @app/state :wss) {:as :value}))]
      (js/console.log "Received:" message)))

  ;; Example of continuous reading
  (async/go-loop []
    (when-let [message (<! (read (-> @app/state :wss) {:as :value}))]
      (js/console.log "Got message:" message)
      (recur))))

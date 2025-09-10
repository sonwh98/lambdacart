(ns lambdacart.grid
  (:require [lambdacart.serde :as serde]
            [lambdacart.stream :as stream]
            [lambdacart.rpc :as rpc]
            [reagent.core :as r]
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
                                 column-key (keyword (:name column))
                                 new-dir (if (= i sort-col)
                                           (if (= sort-dir :asc) :desc :asc)
                                           :asc)
                                 sorted-rows (vec (sort-by #(get % column-key)
                                                           (if (= new-dir :desc) #(compare %2 %1) compare)
                                                           rows))]
                             (swap! grid-state assoc
                                    :rows sorted-rows
                                    :sort-col i
                                    :sort-dir new-dir)))}
         [:span (str (:name column))]
         (when (= i sort-col)
           [:span {:style {:margin-left "8px"}}
            (if (= sort-dir :asc) "▲" "▼")])])))])

(defn handle-key-nav [row-idx col-idx e]
  (let [rows (-> @app/state :grid :rows)
        columns (-> @app/state :grid :columns)
        key->direction {"ArrowLeft" [-1 0]
                        "ArrowRight" [1 0]
                        "ArrowUp" [0 -1]
                        "ArrowDown" [0 1]}
        [dx dy] (get key->direction (.-key e))
        num-of-rows (count rows)
        num-of-cols (count columns)]
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
  (let [columns (get-in @app/state [:grid :columns])
        column (nth columns col-idx)
        column-key (keyword (:name column))
        column-type (:type column)
        {:keys [pred from-str]} column-type
        value (from-str str-value)]
    (when (pred value)
      (swap! app/state assoc-in [:grid :rows row-idx column-key] value))))

(defn cell-component [cell-value row-idx col-idx]
  [:input {:type "text"
           :value (str cell-value)
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
  (let [rows (-> @grid-state :rows)
        columns (-> @grid-state :columns)
        num-of-rows (count rows)
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
             :let [row (nth rows i)]]
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
           (for [[j column] (map-indexed vector columns)
                 :let [column-key (keyword (:name column))
                       cell-value (get row column-key)]]
             ^{:key (str "cell-" i "-" j)}
             [cell-component cell-value i j]))]))]]))

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
      (reset! root (rdc/create-root container)))

    (swap! app/state assoc :context-menu {:visible? false :x 0 :y 0})

    ;; Initialize with empty grid first
    (swap! app/state assoc :grid
           {:rows []
            :columns []
            :selected-rows (sorted-set)
            :sort-col nil
            :sort-dir :asc})

    ;; Render the empty grid immediately
    (rdc/render @root [grid-component (r/cursor app/state [:grid])])))

(defn process-grid-data [response]
  "Process RPC response and update grid state"
  (let [rows (:results response)]
    (js/console.log "Loaded grid data:" rows)
    (when (seq rows)
      (let [row0 (first rows)
            headers (keys row0)]

        (swap! app/state update :grid assoc
               :rows rows
               :columns (mapv (fn [header-kw]
                                {:name header-kw
                                 :type (:str types)})
                              headers))))))

(defn init! []
  (mount-grid)
  (let [wss (stream/map->WebSocketStream {:url "/wsstream"})
        wss (stream/open wss {})]
    (swap! app/state assoc :wss wss)
    ;; Start the response handler
    (rpc/start-response-handler wss)
    ;; Give WebSocket time to connect, then load data
    (async/go
      (<! (async/timeout 100))
      (js/console.log "Loading grid data after WebSocket delay...")
      (let [response (<! (rpc/load-grid-data))]
        (process-grid-data response)))))

(comment

  (-> @app/state keys)

  (get-in @app/state [:grid :columns]))

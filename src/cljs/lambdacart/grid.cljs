(ns lambdacart.grid
  (:require [lambdacart.stream :as stream]
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
  (let [columns (-> @grid-state :columns)
        sort-col (-> @grid-state :sort-col)
        sort-dir (-> @grid-state :sort-dir)]
    [:div {:style {:display "flex"
                   :position "sticky"
                   :top "0"
                   :cursor "pointer"
                   :background "#f0f0f0"
                   :z-index "1"
                   :font-weight "bold"
                   :border-bottom "1px solid #ccc"}}

     [:div {:style {:width "20px" :height "20px" :padding "10px" :border-right "1px solid #ddd"}} ""]
     (doall
      (map-indexed
       (fn [i column]
         [:div {:key (str "h-" (:name column))
                :style (if (:width column)
                         ;; Column has been manually resized - use fixed width
                         {:width (str (:width column) "px")
                          :min-width "50px"
                          :padding "10px"
                          :border-right "1px solid #ddd"
                          :display "flex"
                          :justify-content "space-between"
                          :align-items "center"
                          :position "relative"
                          :box-sizing "border-box"}
                         ;; Column uses default sizing - flex to fill space
                         {:flex "1"
                          :min-width "50px"
                          :padding "10px"
                          :border-right "1px solid #ddd"
                          :display "flex"
                          :justify-content "space-between"
                          :align-items "center"
                          :position "relative"
                          :box-sizing "border-box"})
                :on-click (fn [e]
                            ;; Only sort if not clicking on resize handle
                            (when-not (= (.-target e) (.-currentTarget e))
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
                                       :sort-dir new-dir))))}
          [:span (str (:name column))]
          (when (= i sort-col)
            [:span {:style {:margin-left "8px"}}
             (if (= sort-dir :asc) "▲" "▼")])
          ;; Resize handle
          [:div {:style {:position "absolute"
                         :right "0"
                         :top "0"
                         :bottom "0"
                         :width "4px"
                         :cursor "col-resize"
                         :background "transparent"
                         :z-index 10}
                 :on-mouse-down (fn [e]
                                  (.preventDefault e)
                                  (.stopPropagation e)
                                  (let [start-x (.-clientX e)
                                        header-element (.-parentElement (.-currentTarget e))
                                        start-width (.-offsetWidth header-element)]
                                    (letfn [(handle-mouse-move [move-e]
                                              (let [delta (- (.-clientX move-e) start-x)
                                                    new-width (max 50 (+ start-width delta))]
                                                (swap! grid-state assoc-in [:columns i :width] new-width)))
                                            (handle-mouse-up [_]
                                              (.removeEventListener js/document "mousemove" handle-mouse-move)
                                              (.removeEventListener js/document "mouseup" handle-mouse-up))]
                                      (.addEventListener js/document "mousemove" handle-mouse-move)
                                      (.addEventListener js/document "mouseup" handle-mouse-up))))}]])
       columns))]))

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

(defn save-cell-value [entity-id attribute new-value]
  (rpc/invoke-with-response 'transact
                            [[:db/add entity-id attribute new-value]]))

(defn remove-image-from-item [item-id image-to-remove]
  "Remove an image entity from an item's :item/images"
  (rpc/invoke-with-response 'transact
                            [[:db/retract item-id :item/images (:db/id image-to-remove)]]))

(defn add-image-to-item [item-id image-map]
  "Add a new image entity to an item's :item/images"
  (let [temp-id (str "temp-image-" (random-uuid))]
    (rpc/invoke-with-response 'transact
                              [;; Create the image entity with a temp ID
                               (assoc image-map :db/id temp-id)
                               ;; Link the temp ID to the item
                               [:db/add item-id :item/images temp-id]])))

(defn update-cell [row-idx col-idx str-value]
  (let [columns (get-in @app/state [:grid :columns])
        column (nth columns col-idx)
        column-key (keyword (:name column))
        column-type (:type column)
        {:keys [pred from-str]} column-type
        value (from-str str-value)
        cell-key [row-idx col-idx]
        original-value (get-in @app/state [:grid :rows row-idx column-key])]
    (when (pred value)
      (swap! app/state assoc-in [:grid :rows row-idx column-key] value)
      (if (= value original-value)
        (swap! app/state update-in [:grid :dirty-cells] disj cell-key)
        (swap! app/state update-in [:grid :dirty-cells] (fnil conj #{}) cell-key)))))

(defn save-cell-on-blur [row-idx col-idx]
  (let [row (get-in @app/state [:grid :rows row-idx])
        columns (get-in @app/state [:grid :columns])
        column (nth columns col-idx)
        column-key (keyword (:name column))
        entity-id (:db/id row)
        cell-value (get row column-key)
        cell-key [row-idx col-idx]]
    (when entity-id
      (async/go
        (try
          (js/console.log "Saving cell value on blur:" entity-id column-key cell-value)
          (let [response (<! (save-cell-value entity-id column-key cell-value))]
            (if (:error response)
              (js/console.error "Failed to save cell value:" (:error response))
              (do
                (js/console.log "Cell value saved successfully")
                (swap! app/state update-in [:grid :dirty-cells] disj cell-key))))
          (catch js/Error e
            (js/console.error "Error saving cell value:" e)))))))

(defn load-grid-data []
  (rpc/invoke-with-response 'q '[:find [(pull ?e [:db/id :item/name :item/description :item/price {:item/images [*]}]) ...]
                                 :where
                                 [?e :item/name _]]))

(defn text-cell-renderer [cell-value-cursor row-idx col-idx]
  (let [div-ref (r/atom nil)
        is-focused (r/atom false)]
    (r/create-class
     {:component-did-mount
      (fn [this]
        (when-let [div @div-ref]
          (set! (.-textContent div) (str @cell-value-cursor))))

      :component-did-update
      (fn [this old-argv]
        (let [old-cell-value (nth old-argv 1)
              new-cell-value @cell-value-cursor]
          ;; Only update if not focused and value actually changed
          (when (and @div-ref
                     (not @is-focused)
                     (not= old-cell-value new-cell-value))
            (set! (.-textContent @div-ref) (str new-cell-value)))))

      :reagent-render
      (fn [cell-value-cursor row-idx col-idx]
        @cell-value-cursor ;;deref to trigger reactivivity 
        [:div {:ref #(reset! div-ref %)
               :content-editable true
               :data-row row-idx
               :data-col col-idx
               :style {:width "100%"
                       :height "100%"
                       :min-height "40px"
                       :max-height "200px"
                       :padding "8px"
                       :border "none"
                       :background :inherit
                       :box-sizing "border-box"
                       :outline "none"
                       :font-family "inherit"
                       :font-size "inherit"
                       :line-height "1.4"
                       :white-space "pre-wrap"
                       :word-wrap "break-word"
                       :overflow-wrap "break-word"
                       :overflow-y "auto"
                       :cursor "text"
                       :display "flex"
                       :align-items "flex-start"
                       :margin "0"}
               :suppress-content-editable-warning true
               :on-focus #(do
                            (reset! is-focused true)
                            (when (and (-> @app/state :grid :selected-rows seq)
                                       (not (= (-> @app/state :grid :selected-rows)
                                               row-idx)))
                              (swap! app/state assoc-in [:grid :selected-rows] nil)))
               :on-blur #(do
                           (reset! is-focused false)
                           ;; Get the text content from the editable div
                           (let [text-content (.-textContent (.-target %))]
                             (update-cell row-idx col-idx text-content))
                           (save-cell-on-blur row-idx col-idx))
               :on-key-down #(do
                               ;; Use Ctrl+Enter to save and move to next cell
                               (when (and (= (.-key %) "Enter") (.-ctrlKey %))
                                 (.preventDefault %)
                                 (.blur (.-target %)))
                               ;; Allow normal Enter for line breaks
                               (when (and (= (.-key %) "Enter") (not (.-ctrlKey %)))
                                 ;; Let the default behavior happen (new line)
                                 )
                               (handle-key-nav row-idx col-idx %))
               :on-input #(let [text-content (.-textContent (.-target %))]
                            (update-cell row-idx col-idx text-content))}
         ;;@cell-value-cursor
         ;;div content purposely left empty. content is managed by :component-did-update
         ])})))

(defn image-cell-renderer [cell-value-cursor row-idx col-idx]
  (let [images (if (vector? @cell-value-cursor) @cell-value-cursor [])]
    [:div {:style {:display "flex" :flex-direction "column" :gap "4px" :padding "4px"}}
     (when (seq images)
       [:div {:style {:display "flex" :flex-wrap "wrap" :gap "4px" :margin-bottom "4px"}}
        (doall
         (map-indexed
          (fn [idx image]
            (let [image-url (:image/url image)]
              ^{:key (str "img-" row-idx "-" col-idx "-" idx)}
              [:div {:style {:position "relative" :display "inline-block"}}
               [:img {:src image-url
                      :style {:width "100px"
                              :height "100px"
                              :object-fit "cover"
                              :border-radius "4px"
                              :border "1px solid #ddd"}
                      :on-error #(set! (-> % .-target .-style .-display) "none")}]
               [:button {:style {:position "absolute"
                                 :top "-4px"
                                 :right "-4px"
                                 :width "16px"
                                 :height "16px"
                                 :border-radius "50%"
                                 :border "none"
                                 :background "#ff4444"
                                 :color "white"
                                 :font-size "10px"
                                 :cursor "pointer"
                                 :display "flex"
                                 :align-items "center"
                                 :justify-content "center"}
                         :on-click #(let [image-to-remove image
                                          item-row (get-in @app/state [:grid :rows row-idx])
                                          item-id (:db/id item-row)]
                                      (js/console.log "Removing image:" image-to-remove "from item:" item-id)

                                      ;; Optimistically update UI first
                                      (let [new-images (vec (remove #{image-to-remove} images))]
                                        (reset! cell-value-cursor new-images)
                                        (let [cell-key [row-idx col-idx]]
                                          (swap! app/state update-in [:grid :dirty-cells] (fnil conj #{}) cell-key)))

                                      ;; Make RPC call to remove from database
                                      (async/go
                                        (try
                                          (let [response (<! (remove-image-from-item item-id image-to-remove))]
                                            (if (:error response)
                                              (do
                                                (js/console.error "Failed to remove image from database:" (:error response))
                                                ;; Revert the UI change on error
                                                (reset! cell-value-cursor images))
                                              (do
                                                (js/console.log "Image removed from database successfully")
                                                ;; Clear the dirty flag since we've synced with DB
                                                (let [cell-key [row-idx col-idx]]
                                                  (swap! app/state update-in [:grid :dirty-cells] disj cell-key)))))
                                          (catch js/Error e
                                            (js/console.error "Error removing image:" e)
                                            ;; Revert the UI change on error
                                            (reset! cell-value-cursor images)))))}
                "×"]]))
          images))])

     [:div {:style {:display "flex" :align-items "center" :gap "4px"}}
      [:label {:style {:padding "4px 8px"
                       :border "1px solid #ddd"
                       :border-radius "4px"
                       :background "#f5f5f5"
                       :cursor "pointer"
                       :font-size "12px"}}
       "Upload Image"
       [:input {:type :file
                :accept "image/*"
                :style {:display "none"}
                :on-change #(let [file (-> % .-target .-files (aget 0))]
                              (when file
                                (let [url (js/URL.createObjectURL file)
                                      new-image {:image/url url}
                                      new-images (conj images new-image)
                                      cell-key [row-idx col-idx]]
                                  ;; Optimistically update UI
                                  (reset! cell-value-cursor new-images)
                                  (swap! app/state update-in [:grid :dirty-cells] (fnil conj #{}) cell-key))))}]]]]
        ;; @cell-value-cursor
        ;; div content purposely left empty. content is managed by :component-did-update
    ))

(defn readonly-cell-renderer [cell-value-cursor row-idx col-idx]
  [:div {:style {:padding "8px"
                 :background "#f5f5f5"
                 :color "#666"
                 :border-bottom "1px solid #eee"
                 :cursor "not-allowed"}}
   (str @cell-value-cursor)])

(def types {:int {:pred integer?
                  :from-str js/parseInt
                  :to-str str
                  :renderer text-cell-renderer}
            :str {:pred string?
                  :from-str str
                  :to-str str
                  :renderer text-cell-renderer}
            :float {:pred float?
                    :from-str js/parseFloat
                    :to-str str
                    :renderer text-cell-renderer}
            :image {:pred (fn [value]
                            (and (string? value)
                                 (or (empty? value)
                                     (re-matches #"^https?://.*\.(jpg|jpeg|png|gif|bmp|webp|svg)(\?.*)?$" value))))
                    :from-str str
                    :to-str str
                    :renderer image-cell-renderer}
            :readonly {:pred (constantly true)
                       :from-str str
                       :to-str str
                       :renderer readonly-cell-renderer}})

(defn detect-column-type [column-key sample-value]
  (cond
    (= column-key :db/id) (:readonly types)
    (and (vector? sample-value)
         (every? #(and (map? %) (contains? % :image/url)) sample-value)) (:image types)
    (and (string? sample-value)
         (re-matches #"^https?://.*\.(jpg|jpeg|png|gif|bmp|webp|svg)(\?.*)?$" sample-value)) (:image types)
    (integer? sample-value) (:int types)
    (number? sample-value) (:float types)
    :else (:str types)))

(defn process-grid-data [response]
  (let [rows (:results response)]
    (js/console.log "Loaded grid data:" rows)
    (when (seq rows)
      (let [row0 (first rows)
            headers (keys row0)]
        (swap! app/state update :grid assoc
               :rows rows
               :columns (mapv (fn [header-kw]
                                (let [sample-value (get row0 header-kw)]
                                  {:name header-kw
                                   :type (detect-column-type header-kw sample-value)}))
                              headers))))))

(defn cell-component [row-cursor row-idx col-idx]
  (let [columns-cursor (r/cursor app/state [:grid :columns])
        dirty-cells-cursor (r/cursor app/state [:grid :dirty-cells])
        column (nth @columns-cursor col-idx)
        column-key (keyword (:name column))
        column-type (:type column)
        cell-key [row-idx col-idx]
        is-dirty? (contains? @dirty-cells-cursor cell-key)
        is-db-id? (= column-key :db/id)
        cell-value-cursor (r/cursor row-cursor [column-key])
        renderer (cond
                   is-db-id? (:renderer (:readonly types))
                   :else (:renderer column-type))]
    [:div {:style {:background (when is-dirty? "#fff3cd")}} ; Yellow background for dirty cells
     [renderer cell-value-cursor row-idx col-idx]]))

(defn row-number-component [row-idx selected-rows-cursor]
  (let [context-menu-cursor (r/cursor app/state [:context-menu])]
    [:div {:style {:width "40px"
                   :cursor "pointer"
                   :border-right "1px solid #ddd"
                   :background (if (contains? @selected-rows-cursor row-idx)
                                 "#d4e6f1" ; Darker blue when selected
                                 "#f8f9fa") ; Light gray when not selected
                   :border "1px solid #dee2e6"
                   :border-radius "3px"
                   :font-weight "bold"
                   :font-size "12px"
                   :display "flex"
                   :align-items "center" ; Center vertically
                   :justify-content "center" ; Center horizontally
                   :box-sizing "border-box"
                   :transition "all 0.1s ease"
                   :box-shadow "0 1px 2px rgba(0,0,0,0.1)"
                   :user-select "none"}
           :on-mouse-down #(set! (-> % .-target .-style .-transform) "translateY(1px)")
           :on-mouse-up #(set! (-> % .-target .-style .-transform) "translateY(0)")
           :on-mouse-leave #(set! (-> % .-target .-style .-transform) "translateY(0)")
           :on-click #(swap! selected-rows-cursor
                             (fn [selected]
                               (if (contains? selected row-idx)
                                 (disj selected row-idx)
                                 (conj (or selected (sorted-set)) row-idx))))
           :on-context-menu (fn [e]
                              (.preventDefault e)
                              (when (contains? @selected-rows-cursor row-idx)
                                (reset! context-menu-cursor
                                        {:visible? true
                                         :x (.-clientX e)
                                         :y (.-clientY e)})))}
     (inc row-idx)]))

(defn grid-row-component [row-idx row-cursor columns-cursor selected-rows-cursor]
  [:div {:style {:display "flex"
                 :background (when (contains? @selected-rows-cursor row-idx)
                               "#e8f2ff")}
         :key row-idx}
   [row-number-component row-idx selected-rows-cursor]
   (doall
    (map-indexed
     (fn [col-idx column]
       ^{:key (str "cell-" row-idx "-" col-idx)}
       [:div {:style (if (:width column)
                       {:width (str (:width column) "px")
                        :min-width "50px"
                        :border-right "1px solid #f9f9f9"
                        :box-sizing "border-box"}
                       {:flex "1"
                        :min-width "50px"
                        :border-right "1px solid #f9f9f9"
                        :box-sizing "border-box"})}
        [cell-component row-cursor row-idx col-idx]])
     @columns-cursor))])

(defn grid-component [grid-state context-menu-state]
  [:div {:on-click #(when (:visible? @context-menu-state)
                      (swap! context-menu-state assoc :visible? false))
         :on-context-menu #(.preventDefault %)}
   [header grid-state]
   [:div {:style {:width "100%"
                  :height "90%"
                  :border "1px solid #ccc"
                  :overflow "auto"
                  :font-family "sans-serif"
                  :font-size "14px"
                  :position "relative"}}
    (let [columns-cursor (r/cursor app/state [:grid :columns])
          selected-rows-cursor (r/cursor app/state [:grid :selected-rows])]
      (map (fn [i]
             (let [row-cursor (r/cursor app/state [:grid :rows i])]
               ^{:key (str "row-" i)}
               [grid-row-component i row-cursor columns-cursor selected-rows-cursor]))
           (-> @grid-state :rows count range)))]])

(defn app-component []
  (let [grid-state (r/cursor app/state [:grid])
        context-menu-state (r/cursor app/state [:context-menu])]
    [:div
     [grid-component grid-state context-menu-state]
     [context-menu-component grid-state context-menu-state]]))

(defonce root (atom nil))

(defn mount-grid []
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

    (rdc/render @root [app-component])))

(defn load-and-display-data []
  (async/go
    (js/console.log "Loading grid data...")
    (let [response (<! (load-grid-data))]
      (process-grid-data response)
      (js/console.log "Grid data loaded and displayed"))))

(defn init! []
  (mount-grid)

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
              (load-and-display-data))
            (js/console.error "WebSocket failed to connect after 5 seconds")))))))

;; Add reload function for development
(defn ^:dev/after-load reload! []
  "Called by shadow-cljs after code reload"
  (js/console.log "Code reloaded, refreshing grid...")
  (load-and-display-data))





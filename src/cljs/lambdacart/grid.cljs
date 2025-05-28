(ns lambdacart.grid
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [lambdacart.app :as app]))

(defn header [grid-state]
  [:div {:style {:display "flex"
                 :position "sticky"
                 :top "0"
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

(defn handle-key-nav [grid-state e current-idx current-row num-cols]
  (let [key->direction {"ArrowLeft" [-1 0]
                       "ArrowRight" [1 0]
                       "ArrowUp" [0 -1]
                       "ArrowDown" [0 1]}
        [dx dy] (get key->direction (.-key e))
        rows (-> @grid-state :rows)]
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
                     :border-bottom "1px solid #eee" 
                     :border-right "1px solid #f9f9f9"}
             :on-key-down #(handle-key-nav grid-state % col-idx row-idx 3)
             :on-change #(update-cell grid-state row-idx col-idx (.. % -target -value))}]))

(defn grid-component [grid-state]
  (let [rows (-> @grid-state :rows)]
    [:div
     [header grid-state]
     [:div {:style {:width "100%"
                    :height "90%"
                    :border "1px solid #ccc"
                    :overflow "auto"
                    :font-family "sans-serif"
                    :font-size "14px"
                    :position "relative"}}
      (doall 
       (for [[i row] (map-indexed (fn [i row]
                                    [i row])
                                  rows)]
         [:div {:style {:display "flex"}
                :key i}
          [:div {:style {:width "20px" 
                         :height "20px" 
                         :padding "10px" 
                         :border-right "1px solid #ddd"
                         :background "#f0f0f0" ; Added background color to match header
                         :font-weight "bold"}} ; Added bold font to match header
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
      (swap! app/state assoc-in [:grid :rows ] (vec (for [i (range 5)]
                                                      (mapv str [(rand-int 100) (rand-int 100) (rand-int 100)]))))

      (swap! app/state assoc-in [:grid :header] ["Tour Name" "Description" "Image"]))
    (rdc/render @root [grid-component (r/cursor app/state [:grid])])))

(defn init! []
  (mount-grid))

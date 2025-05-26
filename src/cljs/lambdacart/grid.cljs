(ns lambdacart.grid
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]))

(defn header []
  (let [style {:style {:flex "1" :padding "10px" :border-right "1px solid #ddd"}}]
    [:div {:style {:display "flex"
                   :position "sticky"
                   :top "0"
                   :background "#f0f0f0"
                   :z-index "1"
                   :font-weight "bold"
                   :border-bottom "1px solid #ccc"}}
     [:div {:style {:width "20px" :height "20px" :padding "10px" :border-right "1px solid #ddd"}} ""]
     [:div style "Tour Name"]
     [:div style "Description"]
     [:div style "Image"]]))

(defn find-target-cell [current-cell direction]
  (let [row (.closest current-cell "div[style*='display: flex']")
        cells (array-seq (.querySelectorAll row "[contentEditable='true']"))
        rows (array-seq (.querySelectorAll js/document "div[style*='display: flex']"))
        current-index (.indexOf (vec cells) current-cell)
        current-row-index (.indexOf (vec rows) row)]
    (case direction
      :left  (when (pos? current-index)
               (nth cells (dec current-index)))
      :right (when (< current-index (dec (count cells)))
               (nth cells (inc current-index)))
      :up    (when (pos? current-row-index)
               (let [target-row (nth rows (dec current-row-index))
                     target-cells (array-seq (.querySelectorAll target-row "[contentEditable='true']"))]
                 (nth target-cells current-index)))
      :down  (when (< current-row-index (dec (count rows)))
               (let [target-row (nth rows (inc current-row-index))
                     target-cells (array-seq (.querySelectorAll target-row "[contentEditable='true']"))] 
                 (nth target-cells current-index))))))

(defn handle-key-nav [e]
  (let [key->direction {"ArrowLeft" :left
                        "ArrowRight" :right
                        "ArrowUp" :up
                        "ArrowDown" :down}
        direction (get key->direction (.-key e))]
    (when direction
      (.preventDefault e)
      (when-let [target (find-target-cell (.-target e) direction)]
        (.focus target)))))

(def rows (r/atom (vec (for [i (range 100)]
                         [(rand-int 100) i i]))))

(defn cell-component [value on-change]
  [:input {:type "text"
           :value value
           :style {:flex "1" 
                   :padding "8px" 
                   :border "none"
                   :border-bottom "1px solid #eee" 
                   :border-right "1px solid #f9f9f9"}
           :on-key-down handle-key-nav
           :on-change #(on-change (.. % -target -value))}])

(defn grid-component [rows]
  [:div
   [header]
   [:div {:style {:width "100%"
                  :height "90%"
                  :border "1px solid #ccc"
                  :overflow "auto"
                  :font-family "sans-serif"
                  :font-size "14px"
                  :position "relative"}}
    (doall 
     (for [i (range 100)]
       [:div {:style {:display "flex"}
              :key i}
        [:div {:style {:width "20px" 
                      :height "20px" 
                      :padding "10px" 
                      :border-right "1px solid #ddd"}} 
         (inc i)]
        (let [row (nth @rows i)]
          [:<>
           [cell-component 
            (str (nth row 0)) 
            #(swap! rows assoc-in [i 0] %)]
           [cell-component 
            (str (nth row 1)) 
            #(swap! rows assoc-in [i 1] %)]
           [cell-component 
            (str (nth row 2)) 
            #(swap! rows assoc-in [i 2] %)]])]))]])

(defonce root (atom nil))

(defn mount-grid []
  (when-let [container (.getElementById js/document "app")]
    (when-not @root
      (reset! root (rdc/create-root container)))
    (rdc/render @root [grid-component rows])))

(defn init! []
  (mount-grid))

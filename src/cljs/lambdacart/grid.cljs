(ns lambdacart.grid
  (:require [reagent.core :as r]
            [reagent.dom.client :as rdc]
            [lambdacart.app :as app]))

(def rows (r/atom (vec (for [i (range 100)]
                         [(rand-int 100) i i]))))

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

(defn handle-key-nav [e current-idx current-row num-cols]
  (let [key->direction {"ArrowLeft" [-1 0]
                        "ArrowRight" [1 0]
                        "ArrowUp" [0 -1]
                        "ArrowDown" [0 1]}
        [dx dy] (get key->direction (.-key e))]
    (when (and dx dy)
      (.preventDefault e)
      (let [next-idx (+ current-idx dx)
            next-row (+ current-row dy)
            next-el (when (and (>= next-idx 0) 
                               (< next-idx num-cols)
                               (>= next-row 0)
                               (< next-row (count @rows)))
                      (.querySelector js/document 
                                      (str "[data-row='" next-row "'][data-col='" next-idx "']")))]
        (when next-el
          (.focus next-el))))))

(defn cell-component [value on-change row-idx col-idx]
  [:input {:type "text"
           :value value
           :data-row row-idx
           :data-col col-idx
           :style {:flex "1" 
                   :padding "8px" 
                   :border "none"
                   :border-bottom "1px solid #eee" 
                   :border-right "1px solid #f9f9f9"}
           :on-key-down #(handle-key-nav % col-idx row-idx 3)
           :on-change #(on-change (.. % -target -value))}])

(defn grid-component [state]
  (let [rows (-> @state :grid :rows)]
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
       (for [[i row] (map-indexed (fn [i row]
                                    [i row])
                                  rows)]
         [:div {:style {:display "flex"}
                :key i}
          [:div {:style {:width "20px" 
                         :height "20px" 
                         :padding "10px" 
                         :border-right "1px solid #ddd"}} 
           (inc i)]
          [:<>
           [cell-component 
            (str (nth row 0)) 
            #(swap! rows assoc-in [i 0] %)
            i 0]
           [cell-component 
            (str (nth row 1)) 
            #(swap! rows assoc-in [i 1] %)
            i 1]
           [cell-component 
            (str (nth row 2)) 
            #(swap! rows assoc-in [i 2] %)
            i 2]]]))]]))

(defonce root (atom nil))

(defn mount-grid []
  (when-let [container (.getElementById js/document "app")]
    (when-not @root
      (reset! root (rdc/create-root container))
      (swap! app/state assoc-in [:grid :rows ] (vec (for [i (range 100)]
                                                      [(rand-int 100) i i]))))
    (rdc/render @root [grid-component app/state])))

(defn init! []
  (mount-grid))

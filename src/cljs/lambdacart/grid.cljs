(ns lambdacart.grid)

(defn find-target-cell [current-cell direction]
  (let [row (.closest current-cell "div[style*='display: flex']")
        cells (array-seq (.querySelectorAll row "[contenteditable='true']"))
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
                     target-cells (array-seq (.querySelectorAll target-row "[contenteditable='true']"))] ; Added array-seq here
                 (nth target-cells current-index)))
      :down  (when (< current-row-index (dec (count rows)))
               (let [target-row (nth rows (inc current-row-index))
                     target-cells (array-seq (.querySelectorAll target-row "[contenteditable='true']"))] ; Added array-seq here
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

;; Initialize event listener
(defn init! []
  (.addEventListener js/document "keydown"
                     (fn [e]
                       (when (.-contentEditable (.-target e))
                         (handle-key-nav e)))))

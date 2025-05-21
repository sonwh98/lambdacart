(ns lambdacart.tourism)

(defn init []
  (prn "cljs init"))

(defn ^:export toggleMenu []
  (.. js/document (querySelector ".navigation") -classList (toggle "active")))

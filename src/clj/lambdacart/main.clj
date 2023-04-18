(ns lambdacart.main)

(defn hello [opts]
  (prn "hello 2" opts))

(defn foo [])
(defn foo-bar []

  1
  2)
(defn- main [args]
  (prn "hello"))

(comment
  (+ 1 1)
  (+ 1 2)
  (hello nil)
  (foo))

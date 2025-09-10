(ns lambdacart.serde
  (:require [cognitect.transit :as transit])
  #?(:clj (:import [java.io ByteArrayInputStream ByteArrayOutputStream])))

#?(:clj
   (defn edn->transit
     "Serialize Clojure data to JSON string using transit"
     [data]
     (let [out (ByteArrayOutputStream.)
           writer (transit/writer out :json)]
       (transit/write writer data)
       (.toString out "UTF-8"))))

#?(:cljs
   (defn edn->transit
     "Serialize ClojureScript data to JSON string using transit"
     [data]
     (transit/write (transit/writer :json) data)))

#?(:clj
   (defn transit->edn
     "Deserialize JSON string to Clojure data using transit"
     [json-str]
     (let [in (ByteArrayInputStream. (.getBytes json-str "UTF-8"))
           reader (transit/reader in :json)]
       (transit/read reader))))

#?(:cljs
   (defn transit->edn
     "Deserialize JSON string to ClojureScript data using transit"
     [json-str]
     (transit/read (transit/reader :json) json-str)))

#?(:clj
   (defn serialize-with-transforms
     "Serialize with custom write transforms (Clojure)"
     [data write-transforms]
     (let [out (ByteArrayOutputStream.)
           writer (transit/writer out :json {:transform write-transforms})]
       (transit/write writer data)
       (.toString out "UTF-8"))))

#?(:cljs
   (defn serialize-with-transforms
     "Serialize with custom write transforms (ClojureScript)"
     [data write-transforms]
     (transit/write (transit/writer :json {:transform write-transforms}) data)))

#?(:clj
   (defn deserialize-with-transforms
     "Deserialize with custom read transforms (Clojure)"
     [json-str read-transforms]
     (let [in (ByteArrayInputStream. (.getBytes json-str "UTF-8"))
           reader (transit/reader in :json {:transform read-transforms})]
       (transit/read reader))))

#?(:cljs
   (defn deserialize-with-transforms
     "Deserialize with custom read transforms (ClojureScript)"
     [json-str read-transforms]
     (transit/read (transit/reader :json {:transform read-transforms}) json-str)))

;; Convenience transforms for common use cases
(def datomic-write-transforms
  {"e" (fn [entity] (:db/id entity))}) ; Transform entities to just their IDs

(def datomic-read-transforms
  {"e" (fn [id] {:db/id id})}) ; Transform IDs back to entity maps

#?(:clj
   (def date-write-transforms
     {"d" (fn [date] (.getTime date))})) ; Convert Date to timestamp (JVM only)

#?(:clj
   (def date-read-transforms
     {"d" (fn [timestamp] (java.util.Date. timestamp))})) ; Convert timestamp to Date (JVM only)

#?(:cljs
   (def date-write-transforms
     {"d" (fn [date] (.getTime date))})) ; Convert JS Date to timestamp

#?(:cljs
   (def date-read-transforms
     {"d" (fn [timestamp] (js/Date. timestamp))})) ; Convert timestamp to JS Date

(comment
  (def sample-map {:tours [{:item/name "Ha Long Bay Cruise"
                            :item/price 150
                            :item/description "Beautiful bay cruise"
                            :item/images [{:image/url "http://example.com/img1.jpg"
                                           :image/alt "Bay view"}]}]
                   :metadata {:total-count 1
                              :page 1}})

  (def serialized (edn->transit sample-map))
  (println "Serialized:" serialized)

  (def deserialized (transit->edn serialized))
  (println "Deserialized:" deserialized)
  (= sample-map deserialized)

  (defn roundtrip [x]
    (let [w (transit/writer :json)
          r (transit/reader :json)]
      (transit/read r (transit/write w x)))))

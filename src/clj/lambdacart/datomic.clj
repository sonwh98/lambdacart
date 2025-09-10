(ns lambdacart.datomic
  (:require [datomic.api :as d]
            [clojure.edn :as edn]))

;; Datomic connection setup
(def datomic-user (or (System/getenv "DATOMIC_USER") "datomic"))
(def datomic-password (or (System/getenv "DATOMIC_PASSWORD") "datomic"))

(def db-uri
  (format "datomic:sql://lambdacart?jdbc:postgresql://localhost:5432/datomic?user=%s&password=%s"
          datomic-user datomic-password))
(defonce conn (atom nil))

(defn get-db []
  (when @conn
    (d/db @conn)))

(defn init-datomic! []
  (when-not @conn
    (d/create-database db-uri)
    
    (reset! conn (d/connect db-uri))
    (println "Datomic connection established")))

(def schema
  [{:db/ident       :item/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/ident       :item/description
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/ident       :item/price
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/ident       :item/images
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/many
    :db/doc         "References to image entities associated with the tour"}

   {:db/ident       :image/url
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "URL of the image"}

   {:db/ident       :image/alt
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc         "Alternative text for the image"}])

(def datomic-readers
  {'db/id  datomic.db/id-literal
   'db/fn  datomic.function/construct
   'base64 datomic.codec/base-64-literal})

(comment
  (init-datomic!)
  (d/delete-database db-uri)
  
  @(d/transact @conn schema)
  
  (def sample-data (->> "resources/tours.edn" slurp (edn/read-string  {:readers datomic-readers})))

  @(d/transact @conn sample-data)
  
  (d/q '[:find ?name
         :where [_ :item/name ?name]]
       (get-db))
  (d/q '[:find (pull ?e [:item/name 
                         :item/price 
                         :item/description
                         {:item/images [:image/url :image/alt]}])
         :where [?e :item/name "Ha Long Bay Cruise"]]
       (get-db))

  (d/q '[:find [?e ...] 
        :where 
        [?e :item/name _]]
       (get-db))

  (d/q '[:find [?e ...] :where [?e :item/name _]]
       (get-db))
  )

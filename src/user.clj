(ns user
  (:require [datomic.api :as d]
            [datomic.db]
            [datomic.function]
            [datomic.codec]
            #_[clojure.edn :as edn]))

(def schema
  [{:db/ident       :tour/name
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/ident       :tour/description
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/ident       :tour/price
    :db/valueType   :db.type/long
    :db/cardinality :db.cardinality/one
    :db.install/_attribute :db.part/db}

   {:db/ident       :tour/images
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


(def uri "datomic:mem://people-db")
(def db-uri
  "datomic:sql://lambdacart?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic")

(def datomic-readers
  {'db/id  datomic.db/id-literal
   'db/fn  datomic.function/construct
   'base64 datomic.codec/base-64-literal})

(comment
  ;; Create a new database
  (d/create-database db-uri)
  (d/delete-database db-uri)
  
  ;; Connect to the database
  (def conn (d/connect db-uri))
  (def sample-data (->> "resources/tours.edn" slurp (edn/read-string  {:readers datomic-readers})))

  
  ;; Transact the schema
  @(d/transact conn schema)
  @(d/transact conn sample-data)

  ;; Add data
  @(d/transact conn [{:person/name "Alice" :person/age 30}
                     {:person/name "Bob" :person/age 25}])

  (def db (d/db conn))

  (def results
    (d/q '[:find ?name
           :where
           [?e :person/age ?age]
           [(> ?age 27)]
           [?e :person/name ?name]]
         db))

  (prn results)) 



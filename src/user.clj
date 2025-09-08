(ns user
  (:require [datomic.api :as d]
            [datomic.db]
            [datomic.function]
            [datomic.codec]
            [clojure.edn :as edn]))

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


(def uri "datomic:mem://people-db")


#_(def db-uri "datomic:dev://localhost:4334/lambdacart")


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
  ;; Transact the sample data
  @(d/transact conn sample-data)

  (def db (d/db conn))

    ; Query all tour names
  (d/q '[:find ?name
         :where [_ :item/name ?name]]
       db)
  
  ; Query a specific tour by exact name 
  (d/q '[:find ?e ?price ?desc
         :where 
         [?e :item/name "Ha Long Bay Cruise"]
         [?e :item/price ?price]
         [?e :item/description ?desc]]
       db)
  
  ; Query tours with names containing "Tour"
  (d/q '[:find ?name ?price
         :where
         [?e :item/name ?name]
         [?e :item/price ?price]
         [(clojure.string/includes? ?name "Tour")]]
       db)
  
  ; Query full tour entity with its images
  (d/q '[:find (pull ?e [:item/name 
                         :item/price 
                         :item/description
                         {:item/images [:image/url :image/alt]}])
         :where [?e :item/name "Ha Long Bay Cruise"]]
       db)
  ) 



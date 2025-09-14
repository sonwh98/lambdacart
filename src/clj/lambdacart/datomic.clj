(ns lambdacart.datomic
  (:require [datomic.api :as d]
            [clojure.edn :as edn]))

;; Datomic connection setup
(def datomic-user (or (System/getenv "DATOMIC_USER") "datomic"))
(def datomic-password (or (System/getenv "DATOMIC_PASSWORD") "datomic"))

(def db-uri
  (format "datomic:sql://lambdacart?jdbc:postgresql://localhost:5432/datomic?user=%s&password=%s"
          datomic-user datomic-password))
(def conn (atom nil))

(defn get-db []
  (when @conn
    (d/db @conn)))

(defn reset-datomic! []
  "Deletes and recreates the database with a fresh connection"
  (when @conn
    (println "Closing existing connection..."))
  (reset! conn nil)
  (d/delete-database db-uri)
  (println "Database deleted")
  (d/create-database db-uri)
  (println "Database created")
  (reset! conn (d/connect db-uri))
  (println "New connection established"))


(defn init-datomic! []
  (when-not @conn
    (d/create-database db-uri)
    (reset! conn (d/connect db-uri))
    (println "Datomic connection established")))

(def datomic-readers
  {'db/id datomic.db/id-literal
   'db/fn datomic.function/construct
   'base64 datomic.codec/base-64-literal})

(defn get-connection []
  "Returns the Datomic connection for transactions"
  @conn)

(comment
  (def conn (atom nil))
  (init-datomic!)
  (reset-datomic!)
  (d/create-database db-uri)
  (d/delete-database db-uri)
  
  (def schema (->> "resources/schema.edn" slurp (edn/read-string {:readers datomic-readers})))
  @(d/transact @conn schema)

  
  (def tt (->> "resources/tt-cosmetics.edn" slurp (edn/read-string {:readers datomic-readers})))
  @(d/transact @conn tt)

  (def sample-data (->> "resources/tours.edn" slurp (edn/read-string {:readers datomic-readers})))

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
       (get-db)))

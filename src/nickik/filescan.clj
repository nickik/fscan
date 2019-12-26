(ns nickik.filescan
  (:gen-class)
  (:require [me.raynes.fs :as f]
            [digest :as d]
            [parallel.core :as p]
            [datahike.api :as dh]
            [clojure.tools.trace :refer :all]))

(def uri (str "datahike:file:///tmp/example"))

(def excludes [".git" ".idea" ".gitignore"])

(defn exclude-paths? [file]
  (some identity (map #(.. file getAbsolutePath (contains %)) excludes)))

(defn to-path-trx [{:keys [hash-str] :as file}]
  (assoc (select-keys file [:path-str])
         :path-hash-ref [:hash-str hash-str]))

(defn  to-hash-trx [file]
  (select-keys file [:hash-str]))

(defn resolve-file [file]
  {:path-str  (. file getAbsolutePath)
   :hash-str (d/md5 file)})

(def schema
  [;; File
   {:db/ident :path-str
    :db/valueType :db.type/string
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident :path-hash-ref
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   ;; Hash
   {:db/ident :hash-str
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity}])

(defn -main [& args]
  (dh/delete-database uri)
  (dh/create-database uri)
  (let [path (first args)
        structure (file-seq (clojure.java.io/file path))
        conn (dh/connect uri)
        valid-file-seq
        (sequence
         (comp
          (remove f/directory?)
          (remove exclude-paths?)
          (map resolve-file))
         structure)]
    (println "path: " path)
    (println "db: " uri)

    #_(clojure.pprint/pprint valid-file-seq)
    (dh/transact conn schema)
    #_(clojure.pprint/pprint (mapv to-hash-trx valid-file-seq))
    (dh/transact conn (mapv to-hash-trx valid-file-seq))
    #_(println "--------1---------")
    #_(clojure.pprint/pprint (mapv to-path-trx valid-file-seq))
    (dh/transact conn (mapv to-path-trx valid-file-seq))
    (println "--------2---------")

    (into {}
          (filter
            (fn [[_ v]] (< 1 (count v)))                          ;; Only multible uses
            (group-by (comp :hash-str :path-hash-ref)
                      (flatten (dh/q '[:find  (pull ?e [:path-str {:path-hash-ref [:hash-str]}])
                                       :where
                                       [?e :path-str ?n]]
                                     @conn)))))



    ))

#_(dh/q '[:find  (pull ?e [*]) #_(pull ?e [:path-str {:path-hash-ref [:hash-str]}])
          :where
          [?e :hash-str ?n]]
        @(dh/connect uri))

#_(-main "/home/nick/Desktop")
(println "Run ---->")
(time (-main "/home/nick/alltech/project"))


(ns nickik.filescan
  (:gen-class)
  (:require [me.raynes.fs :as f]
            [digest :as d]
            [parallel.core :as p]
            [clojure.tools.trace :refer :all]))

(def uri (str "datahike:file:///tmp/example"))

(def excludes [".git" ".idea" ".gitignore"])

(defn exclude-paths? [file]
  (some identity (map #(.. file getAbsolutePath (contains %)) excludes)))

(defn to-path-trx [{:keys [hash-str] :as file}]
  (assoc (select-keys file [:path-str])
         :path-hash-ref [:hash-str hash-str]))

(defn to-hash-trx [file]
  (select-keys file [:hash-str]))

(defn resolve-file [file]
  {:path-str (. file getAbsolutePath)
   :hash-str (d/md5 file)})

(def schema
  [;; File
   {:db/ident       :path-str
    :db/valueType   :db.type/string
    :db/unique      :db.unique/identity
    :db/cardinality :db.cardinality/one}

   {:db/ident       :path-hash-ref
    :db/valueType   :db.type/ref
    :db/cardinality :db.cardinality/one}

   ;; Hash
   {:db/ident       :hash-str
    :db/valueType   :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique      :db.unique/identity}])

(defn map-over-v [m f]
  (into {} (for [[k v] m] [k (f v)])))

(defn enrich [m]
  {:count (count m)
   :paths (mapv :path-str m)
   :hash  (:hash-str (first m))})

(defn jfdalkf [hash-group dfja]
  #_(println "-> " dfja  " " (type dfja) "   " (int dfja))
  (hash-map :path (get (:paths hash-group) (int dfja))
            :operation :delete))

(defn handle-user-request! [hash-group user-input]
  (->> (clojure.string/split user-input #",")
       (map #(Integer/parseInt %))
       (map (partial jfdalkf hash-group))))

(defn print-return [x]
  (println) x)

(defn -main [& args]
  (let [path (first args)
        structure (file-seq (clojure.java.io/file path))
        files (sequence
               (comp
                (remove f/directory?)
                (remove exclude-paths?)
                (map resolve-file))
               structure)
        by-hash (p/group-by :hash-str files)
        valid-file-seq
        (map-over-v by-hash enrich)
        commands (atom [])]
    (doseq [hash-group (vals valid-file-seq)]
      (when (< 1 (:count hash-group))
        (doseq [path-tuple (map-indexed (fn [i path]
                                          [i path]) (:paths hash-group))]
          (println (first path-tuple) ": " (second path-tuple)))
        (println)
        (print "Enter Paths to be removed [Example: 1,2]")
        (flush)
        (->> (read-line)
             print-return
             (handle-user-request! hash-group)
             (swap! commands conj))))
    (doseq [path (map :path (apply concat @commands))]
      (println "rm " path)
      (f/delete path))
    (println)))

(-main "/home/nick/Desktop")
#_(-main "/home/nick/Desktop")
#_(time)


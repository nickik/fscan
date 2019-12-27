(ns nickik.filescan
  (:gen-class)
  (:require [me.raynes.fs :as f]
            [parallel.core :as p]
            [cli-matic.core :refer [run-cmd]]
            [clojure.data.json :as json])
  (:import (net.jpountz.xxhash XXHashFactory)
           (java.io FileInputStream)
           (java.io UnsupportedEncodingException)
           (java.io IOException)))

(set! *warn-on-reflection* true)

(def ^:static xx-seed 4985495)

(def ^XXHashFactory xx-hash-factory (. XXHashFactory fastestInstance))

(defn  ^FileInputStream file-stream [^java.io.File file]
  (FileInputStream. file))

(defn xx-hash [file]
  (let [hash64   (. xx-hash-factory newStreamingHash64 xx-seed)
        buffer-block (byte-array 8192)
        file-input-stream (file-stream file)]
    (try
      (loop [read (.read file-input-stream buffer-block)]
        (when (not= read -1)
          (.update hash64 buffer-block 0 read)
          (recur (.read file-input-stream buffer-block))))
      (.close file-input-stream)
      (.getValue hash64)                          ;; Hash
      (catch UnsupportedEncodingException e (str "caught exception: " (.getMessage e)))
      (catch IOException e (str "caught exception: " (.getMessage e))))))

(defn resolve-file [^java.io.File file]
  (assert (.isAbsolute file) "Paths must be Absolute")
  {:path-str (.getPath file)
   :hash     (xx-hash file)})

(defn internal-file [hash-group index]
  (hash-map :path (get (:paths hash-group) index)
            :operation :delete))

(defn handle-user-request! [hash-group user-input]
  (->> (clojure.string/split user-input #",")
       (map #(Integer/parseInt %))
       (map (partial internal-file hash-group))))

(defn print-return [x]
  (println) x)

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(defn paths [patterns {:keys [path-str]}]
  (some identity (map #(re-find (re-pattern %) path-str) patterns)))

(defn to-hash-group [include exclude file-seq]
  (let [files (->> file-seq
               (remove f/directory?)
               (map resolve-file)
               (remove (partial paths exclude))
               (filter (partial paths include)))
        by-hash (p/group-by :hash files)]
    (map
     (fn [m]
       {:count (count m)
        :paths (mapv :path-str m)
        :hash  (:hash (first m))})
     (vals by-hash))))

(defn more-then-one [hash-group]
  (< 1 (:count hash-group)))

(defn user-input-to-cmd [hash-group]
  (when (more-then-one hash-group)
    (doseq [path-tuple (map-indexed (fn [i path]
                                      [i path]) (:paths hash-group))]
      (println (first path-tuple) ": " (second path-tuple)))
    (println)
    (print "Enter Paths to be removed [Example: 0,1]: ")
    (flush)
    (->> (read-line)
         print-return
         (handle-user-request! hash-group))))

(defn path-to-file-seq [path]
  (file-seq (clojure.java.io/file path)))

(def merge-with-into (partial merge-with into))

(defn parse-comma-lst [csl]
  (clojure.string/split (if csl csl "") #","))

(defn to-hash-groups [paths include exclude]
  (let [include (parse-comma-lst include)
        exclude (parse-comma-lst exclude)]
    (->> paths
         (map path-to-file-seq)
         (map #(to-hash-group include exclude %))
         (apply merge-with-into))))

(defn filescan-remove [{:keys [paths interactive include exclude]
                        :as input}]
  (when-not interactive
    (println "Non Interactive Remove is not implemented")
    (System/exit 0))

  (clojure.pprint/pprint input)
  (let [hash-groups (to-hash-groups paths include exclude)
        commands (mapcat user-input-to-cmd hash-groups)]
    (doseq [path (map :path commands)]
      (println "rm -f" path)
      #_(f/delete path))))

(defn print-human-readable [hash-groups]
  (doseq [hg hash-groups]
    (when (more-then-one hg)
      (println (:hash hg))
      (doseq [p (:paths hg)]
        (println  "\t" p))
      (println))))

(defn write-output [hash-groups format]
  (condp = format
    :human (print-human-readable hash-groups)
    :edn (clojure.pprint/pprint hash-groups)
    :json (json/write-str hash-groups)))

(defn filescan-print [{:keys [paths format output append includes excludes]}]
  (let [hash-groups (to-hash-groups paths includes excludes)]
    (when output
      (assert (not (f/directory? output)) "Can not write Output to a directory!"))
    (binding [*out*  (if output (clojure.java.io/writer output :append append)
                                *out*)]
      (write-output hash-groups format))))

(def cli-config
  {:app         {:command     "filescan - fscan"
                 :description "Find and removes duplicate files"
                 :version     "0.0.1"}
   :global-opts [{:option  "interactive"
                  :short   "i"
                  :as      "User choice of what file to remove"
                  :type    :flag
                  :default true}
                 {:option "paths"
                  :short "p"
                  :as "List of Paths to be scanned"
                  :type :string
                  :default :present
                  :multiple true}
                 {:option "exclude"
                  :as "List of Regex, for Blacklist [Example: .git,.cache]"
                  :type :string}
                 {:option "include"
                  :as "List of Regex for Whitelist [Example: home]"
                  :type :string}]
   :commands    [{:command     "remove"
                  :short       "d"
                  :description ["Delete duplicate from provided paths"]
                  :opts        []
                  :runs        filescan-remove}
                 {:command     "print"
                  :short       "p"
                  :description ["Prints duplicate from provided paths (not interactive)"]
                  :opts        [{:option "format" :short "f" :as "Format of Output" :type :keyword :default :human}
                                {:option "output" :short "o" :as "Location of Output File" :type :string}
                                {:option "append" :short "a" :as "Appends to Output File" :type :flag :default :false}]
                  :runs        filescan-print}]})

(defn -main [& args]
  (run-cmd args cli-config))

(comment
    (def test-path-small "/home/nick/Downloads/wnf")
    (def test-path-big "/home/nick/Downloads/test")

    (defn do-bench [path]
      (println "Use XX Hash: ")
      (time (to-hash-group (path-to-file-seq path))))

    (do-bench test-path-small))

#_(time)


(ns nickik.filescan
  (:gen-class)
  (:require [me.raynes.fs :as f]
            [parallel.core :as p]
            [clojure.data.json :as json])
  (:import (net.jpountz.xxhash XXHashFactory)
           (java.io FileInputStream)
           (java.io UnsupportedEncodingException)
           (java.io IOException)))

(set! *warn-on-reflection* true)

(def ^:static xx-seed 4985495)

(def ^XXHashFactory xx-hash-factory (. XXHashFactory fastestJavaInstance))

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

(defn paths [include exclude {:keys [path-str]}]
  (let [include-matches (map #(re-find (re-pattern %) path-str) include)
        _ (println include-matches  )
        exclude-matches (map #(re-find (re-pattern %) path-str) exclude)
        _ (println exclude-matches)])
  false)

(defn resolve-files [include exclude file-seq]
(->> file-seq
     (remove f/directory?)
     (p/pmap resolve-file)
     #_(remove (partial paths include exclude))))

(defn enrich-result [m]
  {:count (count m)
   :paths (mapv :path-str m)
   :hash  (:hash (first m))})

(defn hash-path-lst-to-hash-group [hash-path-lst]
  (->> hash-path-lst
       (group-by :hash)
       vals
       (map enrich-result)))

(defn files-to-hash-group [include exclude file-seq]
  (-> (resolve-files include exclude file-seq)
      hash-path-lst-to-hash-group))

(defn more-then-one [hash-group]
  (< 1 (:count hash-group)))

(defn user-input-to-cmd [hash-group]
  (when (more-then-one hash-group)
    (doseq [path-tuple (map-indexed (fn [i path]
                                      [i path]) (:paths hash-group))]
      (println "\t" (first path-tuple) ": " (second path-tuple)))
    (println)
    (print "Enter Paths to be removed [Example: 0,1]: ")
    (flush)
    (->> (read-line)
         print-return
         (handle-user-request! hash-group))))

(defn path-to-file-seq [path]
  (println path)
  (file-seq (clojure.java.io/file path)))

(def merge-with-into (partial merge-with into))

(defn parse-comma-lst [csl]
  (clojure.string/split (if csl csl "") #","))

(defn to-hash-groups [paths include exclude]
  (->> paths
       (map path-to-file-seq)
       (map #(files-to-hash-group
               (parse-comma-lst include)
               (parse-comma-lst exclude)
               %))
       (apply merge-with-into)))


(defn filescan-remove [{:keys [paths interactive include exclude] :as input}]
  (when-not interactive
    (println "Non Interactive Remove is not implemented")
    (System/exit 0))
  (let [hash-groups (to-hash-groups paths include exclude)
        #_(clojure.pprint/pprint hash-groups)
        commands (mapcat user-input-to-cmd hash-groups)
        #_(clojure.pprint/pprint commands)]
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
#_(def cli-config
  {:app         {:command     "filescan - fscan"
                 :description "Find and removes duplicate files"
                 :version     "0.0.1"}
   :global-opts [{:option  "interactive"
                  :short   "i"
                  :as      "User choice of what file to remove"
                  :type    :flag
                  :default true}
                 {:option "paths"
                  :as "List of Paths to be scanned"
                  :short "p"
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
                  :description ["Delete duplicate from provided paths"]
                  :opts        []
                  :runs        filescan-remove}
                 {:command     "print"
                  :description ["Prints duplicate from provided paths (not interactive)"]
                  :opts        [{:option "format" :short "f" :as "Format of Output" :type :keyword :default :human}
                                {:option "output" :short "o" :as "Location of Output File" :type :string}
                                {:option "append" :short "a" :as "Appends to Output File" :type :flag :default :false}]
                  :runs        filescan-print}]})

(defn simple-args-parse [args]
  {:interactive true
   :paths args})

(defn print-help []
  (println "fscan expects a absolute path to a directory as an input!"))

(defn -main [& args]
  (when (nil? args)
     (print-help)
     (System/exit 0))
  (when (not (f/directory? (first args)))
    (print-help)
    (System/exit 0))

  (filescan-remove (simple-args-parse args)))

(comment
    (def test-path-small "/home/nick/Downloads/wnf")
    (def test-path-big "/home/nick/Downloads/test")

    (defn do-bench [path]
      (println "Use XX Hash: ")
      (time (files-to-hash-group (path-to-file-seq path))))

    (do-bench test-path-small))


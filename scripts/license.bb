#!/usr/bin/env bb

; A naive script to prepend license lines to source files

(require '[clojure.java.io :as io])

(def license-lines ["; Copyright. 2022, Flybot Pte. Ltd." 
                    "; Apache License 2.0, http://www.apache.org/licenses/"
                    ""])

(defn clojure-files [dir]
  (for [file (file-seq (io/file dir))
        :let [file-name (.getName file)]
        :when (.endsWith file-name ".clj")]
    file))

(defn licensed? [f]
  (->> (take (count license-lines) (line-seq (io/reader f)))
       (= license-lines)))

(defn prepend-license-lines [f]
  (let [lines (->> (interleave license-lines (repeat "\n")) (apply str))
        content (str lines (slurp f))]
    (with-open [wtr (io/writer f)]
      (.write wtr content))))

(defn main []
  (doseq [f (clojure-files "src")]
    (when-not (licensed? f)
      (println "Working on: " (.getName f))
      (prepend-license-lines f))))

(comment
  (clojure-files "src")
  (licensed? (io/file "bb.edn"))
  (prepend-license-lines (io/file "bb.edn"))
  (main)
  )

(main)
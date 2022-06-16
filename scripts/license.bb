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
  (let [lines (->> (interleave license-lines (repeat "\n")) (apply str))]
    (->> (str lines (slurp f))
         (spit (.getName f)))))

(defn main []
  (doseq [f (clojure-files "src")]
    (when-not (licensed? f)
      (prepend-license-lines f))))

(comment
  (clojure-files "src")
  (licensed? (io/file "bb.edn"))
  (prepend-license-lines (io/file "bb.edn"))
  (main)
  )
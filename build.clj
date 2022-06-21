(ns build
  "Build script for this project"
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as cb]
            [marginalia.main :as marg]))

(def lib 'sg.flybot/lasagna-pull)
(def version (format "0.3.%s" (b/git-count-revs nil)))
(def url "https://github.com/flybot-sg/lasagna-pull")

(defn source-files
  [dir]
  (->> (file-seq (java.io.File. dir))
       (map str)
       (filter #(re-matches #".*?\.clj.?+" %))
       (map #(str "./" %))))

(defn docs
  "Produce source documentations."
  [_]
  (cb/clean {:target "docs"})
  (apply marg/-main (source-files "src")))

(defn ci
  [opts]
  (-> opts
      (assoc :lib lib :version version :scm {:url url})
      (cb/clean)
      (cb/run-tests)
      (cb/jar)))

(defn deploy
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (cb/deploy)))

(ns build
  "Build script for this project"
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as cb]))

(def lib 'robertluo/pullable)
(def version (format "0.3.%s" (b/git-count-revs nil)))

(defn tests
  [opts]
  (cb/run-tests opts))

(defn ci
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (cb/clean)
      (cb/run-tests)
      (cb/jar)))

(defn deploy
  [opts]
  (-> opts
      (assoc :lib lib :version version)
      (cb/deploy)))

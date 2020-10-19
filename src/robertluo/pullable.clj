(ns robertluo.pullable
  "Pull from data structure by using pattern."
  (:require
   [robertluo.pullable
    [core :as core]
    [pattern :as ptn]
    option]))

(defn query
  "Returns a query from `pattern` and `global-options` map. A query can be used to
   pull from data structure later.

  ### Pattern

  A pattern is recursive data structure, consists of:

   - Simple value pattern, just be used as a key of a lookup data structure, such as map keys, vector index, etc.
   - Vector pattern, returns the combination of its elements.
   - Join pattern, aka map, its keys are patterns, and will be ran first, then the values (also are patterns) will be run in the context of the key result data structure.
   - List pattern, first element is a pattern, the rest are options, must be in pairs.
 "
  [pattern]
  (ptn/-as-query pattern))

(defn run
  "Given `data`, run a query returned by `query` and returns the pull result."
  [data q]
  (core/-transform q (empty data) data))

(defn pull
  "Combined `query` and `run` in one step."
  ([data ptn]
   (run data (query ptn))))

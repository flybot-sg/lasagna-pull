(ns robertluo.pullable
  "Pull from data structure by using pattern."
  (:require
   [robertluo.pullable.query :as query]))

(defn query
  "Returns a query from `pattern` and `global-options` map. A query can be used to
   pull from data structure later.

  ### Pattern

  A pattern is recursive data structure, consists of:

   - Simple value pattern, just be used as a key of a lookup data structure, such as map keys, vector index, etc.
   - Vector pattern, returns the combination of its elements.
   - Join pattern, aka map, its keys are patterns, and will be ran first, then the values (also are patterns) will be run in the context of the key result data structure.
   - List pattern, first element is a pattern, the rest are options, must be in pairs.

  ### Global options

  A nullable map, will be applied to every sub-pattern in the pattern as if they have all list pattern.
  "
  [pattern global-options]
  (cond->> pattern
    (seq global-options) (query/rewrite-pattern global-options)
    true                 (query/-as-query)))

(defn run
  "Given `data`, run a query returned by `query` and returns the pull result."
  [data q]
  (query/-transform q (empty data) data))

(defn pull
  "Combined `query` and `run` in one step."
  ([data ptn]
   (pull data ptn nil))
  ([data ptn global-options]
   (run data (query ptn global-options))))

(ns robertluo.pullable
  "Pull from data structure by using pattern."
  (:require
   [robertluo.pullable.query :as query]))

(defn query
  "Returns a query from `pattern`. A query can be used to
   pull from data structure later. "
  [pattern]
  (query/compile-x pattern))

(defn run
  "Given `data`, run a query returned by `query` and returns the pull result."
  [q data]
  (query/run q data))

(comment
  (def q (query {:a '? :b '?}))
  (run '{:a ? :b ?} {:a 3 :b 2})
  )
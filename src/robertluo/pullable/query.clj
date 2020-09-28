(ns robertluo.pullable.query)

(defprotocol Query
  (-key [query]
    "returns the key of the query")
  (-value-of [query m]
    "run query on m, return the value")
  (-transform [this target m]
    "run query on m, returns the transformed target"))

(defrecord SimpleQuery [k]
  Query
  (-key [_] k)
  (-value-of [_ m]
    (get m k ::none))
  (-transform [this target m]
    (conj target [(-key this) (-value-of this m)])))

(defrecord JoinQuery [k-query v-query]
  Query
  (-key [_] (-key k-query))
  (-value-of [_ m]
    (let [v (-value-of k-query m)]
      (if (= v ::none)
        ::none
        (-transform v-query {} v))))
  (-transform [this target m]
    (conj target [(-key this) (-value-of this m)])))

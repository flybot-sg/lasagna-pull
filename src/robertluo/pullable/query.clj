(ns robertluo.pullable.query)

(defprotocol Query
  (-key [query]
    "returns the key of the query")
  (-value-of [query m]
    "run query on m, return the value")
  (-transform [this target m]
    "run query on m, returns the transformed target"))

(defprotocol Findable
  (-select [target k not-found]
    "returns value of target on k"))

(defprotocol Target
  (-append [target k v]
    "append value to target"))

(defrecord SimpleQuery [k]
  Query
  (-key [_] k)
  (-value-of [_ m]
    (-select m k ::none))
  (-transform [this target m]
    (-append target (-key this) (-value-of this m))))

(defrecord JoinQuery [k-query v-query]
  Query
  (-key [_] (-key k-query))
  (-value-of [_ m]
    (let [v (-value-of k-query m)]
      (if (= v ::none)
        ::none
        (-transform v-query (empty v) v))))
  (-transform [this target m]
    (-append target (-key this) (-value-of this m))))

(defrecord VectorQuery [queries]
  Query
  (-key [_] ::none)
  (-value-of [_ m]
    (map #(-value-of % m) queries))
  (-transform [this target m]
    (reduce (fn [t q] (-transform q t m))
            target
            queries)))

(defrecord AsQuery [query k]
  Query
  (-key [_] k)
  (-value-of [_ m] (-value-of query m))
  (-transform [this target m]
    (-append target (-key this) (-value-of query m))))

;=======================================================
; Implements Findable/Target on common data structures 

(extend-protocol Findable
  clojure.lang.ILookup
  (-select [this k not-found]
    (.valAt this k not-found))

  clojure.lang.IPersistentVector
  (-select [this k not-found]
    (map #(-select % k not-found) this)))

(defn pad
  "returns a coll which has `n` length, with `coll` fill in first, then pad with
  value"
  [n coll value]
  (take n (concat coll (repeat value))))


(extend-protocol Target
  nil
  (-append [this k v]
    (assoc this k v))

  clojure.lang.IPersistentMap
  (-append [this k v]
    (.assoc this k v))

  clojure.lang.IPersistentVector
  (-append [this k v]
    (mapv #(-append % k %2)
          (pad (count v) this nil)
          v)))

(ns robertluo.pullable.query
  (:import [clojure.lang IPersistentVector IPersistentMap IPersistentList
            ILookup Sequential IPersistentSet APersistentVector]))

;; Pullable implemented by interaction between
;; Query and Findable (data source) and Target (data sink)
;; By define them as protocols, user can extend them to support
;; customized behavior on their own data structures.

(defprotocol Query
  "Can query on a data structure, return the value of its key, also able
   to transform another data structure"
  (-key [query]
    "returns the key of the query, should be a vector")
  (-value-of [query m]
    "run query on m, return the value")
  (-transform [this target m]
    "run query on m, returns the transformed target"))

(defprotocol Findable
  "Source of data"
  (-select [target k not-found]
    "returns value of target on k"))

(defprotocol Target
  "Sink of data"
  (-append [target k v]
    "append value to target"))

(defn default-transform
  [query target m]
  (-append target (-key query) (-value-of query m)))

(defrecord SimpleQuery [k]
  Query
  (-key [_] [k])
  (-value-of [_ m]
    (-select m k ::none))
  (-transform [this target m]
    (default-transform this target m)))

(defn- join-transform
  [k-query v-query target m]
  (let [v (-value-of k-query m)]
    (-append target (-key k-query)
             (if (= v ::none)
               ::none
               (-transform v-query (empty v) v)))))

(defrecord JoinQuery [k-query v-query]
  Query
  (-key [_] (concat (-key k-query) (-key v-query)))
  (-value-of [_ m]
    (let [v (-value-of k-query m)]
      (if (= v ::none)
        ::none
        (-value-of v-query v))))
  (-transform [this target m]
    (join-transform k-query v-query target m)))

(defrecord VectorQuery [queries]
  Query
  (-key [_] [(mapcat -key queries)])
  (-value-of [_ m]
    (map #(-value-of % m) queries))
  (-transform [this target m]
    (reduce (fn [t q] (-transform q t m))
            target
            queries)))

(defmulti create-option
  "Create query option"
  :option/type)

(defprotocol QueryStatement
  (-as-query [statement]
    "create a query from statement"))

(defn- make-options
  [query opt-pairs]
  (reduce (fn [q [ot ov]]
            (create-option {:option/query q :option/type ot :option/arg ov}))
          query opt-pairs))

(defn- option-map [x]
  (let [[q & options] x]
    [q (->> options (partition 2) (mapv vec) (into {}))]))

(extend-protocol QueryStatement
  Object
  (-as-query [this]
    (->SimpleQuery this))
  IPersistentVector
  (-as-query [this]
    (->VectorQuery (map -as-query this)))
  IPersistentMap
  (-as-query [this]
    (let [[k v] (first this)]
      (->JoinQuery (-as-query k) (-as-query v))))
  IPersistentList
  (-as-query [this]
    (let [[q opt-pairs] (option-map this)
          query         (-as-query q)]
      (make-options query opt-pairs))))

;=======================================================
; Implements Findable/Target on common data structures

(extend-protocol Findable
  ILookup
  (-select [this k not-found]
    (.valAt this k not-found))

  ;;FIXME clojure's sequence has many different interfaces
  Sequential
  (-select [this k not-found]
    (map #(-select % k not-found) this))

  ;;FIXME a vector is also ILookup enabled, choose this sacrificed select
  ;;element based on index
  APersistentVector
  (-select [this k not-found]
    (map #(-select % k not-found) this))

  IPersistentSet
  (-select [this k not-found]
    (map #(-select % k not-found) this)))

(defn pad
  "returns a coll which has `n` length, with `coll` fill in first, then pad with
  value"
  [n coll value]
  (take n (concat coll (repeat value))))

(defn- seq-append
  [coll k v]
  (when-not (coll? v)
    (throw (ex-info "impossible v" {:v v})))
  (into (empty coll)
        (map #(-append % k %2)
             (pad (count v) coll nil)
             v)))

(def ^:const ignore ::ignore)

(defn- append [x k v]
  (if (not= v ignore)
    (assoc-in x k v)
    x))

(extend-protocol Target
  nil
  (-append [this k v]
    (append this k v))

  IPersistentMap
  (-append [this k v]
    (append this k v))

  Sequential
  (-append [this k v]
    (seq-append this k v))

  IPersistentSet
  (-append [this k v]
    (seq-append this k v)))

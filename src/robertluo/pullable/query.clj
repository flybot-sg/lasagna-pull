(ns robertluo.pullable.query
  (:import [clojure.lang IPersistentVector IPersistentMap IPersistentList
            ILookup]))

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

;;====================================
;; Options are wrapper of another query

(defrecord AsOption [query k]
  Query
  (-key [_] k)
  (-value-of [_ m] (-value-of query m))
  (-transform [this target m]
    (-append target (-key this) (-value-of this m))))

(defrecord NotFoundOption [query not-found]
  Query
  (-key [_] (-key query))
  (-value-of [_ m]
    (let [v (-value-of query m)]
      (if (= v ::none)
        not-found
        v)))
  (-transform [this target m]
    (let [v (-value-of this m)]
      (if (not= v 'ignore)
        (-append target (-key this) v)
        target))))

(defrecord SeqOption [query offset limit]
  Query
  (-key [_] (-key query))
  (-value-of [_ m]
    (let [v (-value-of query m)]
      (if (seqable? v)
        (cond->> v
          offset (drop offset)
          limit (take limit))
        (throw (ex-info "value not seqable" {:value v})))))
  (-transform [this target m]
    (-append target (-key this) (-value-of this m))))

(defrecord WithOption [query args]
  Query
  (-key [_] (-key query))
  (-value-of [_ m]
    (let [v (-value-of query m)]
      (if (fn? v)
        (apply v args)
        (throw (ex-info "value is not a function" {:value v})))))
  (-transform [this target m]
    (-append target (-key this) (-value-of this m))))

(defn query
  [query m]
  (-transform query (empty m) m))

(defn pattern-error [msg data]
  (ex-info msg data))

(defprotocol QueryStatement
  (-as-query [statement]
    "create a query from statement"))

(defmulti create-option
  "Create query option"
  :option/type)

(defmethod create-option :as
  [{:option/keys [arg query]}]
  (->AsOption query arg))

(defmethod create-option :not-found
  [{:option/keys [query arg]}]
  (->NotFoundOption query arg))

(defmethod create-option :seq
  [{:option/keys [query arg]}]
  (let [[offset limit] arg]
    (->SeqOption query offset limit)))

(defmethod create-option :with
  [{:option/keys [query arg]}]
  (when (not (vector? arg))
    (throw (pattern-error "with args should be a vector" {:arg arg})))
  (->WithOption query arg))

#_(defmethod create-option :batch
  [{:option/keys [query arg]}]
  (when (and (not (vector? arg)) (every? vector? arg))
    (throw (pattern-error "batch requires a vector of vector arguments" {:arg arg})))
  ())

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
    (let [[q & options] this
          opt-pairs (partition 2 options)
          query (-as-query q)]
      (reduce (fn [q [ot ov]]
                (create-option {:option/query q :option/type ot :option/arg ov}))
              query opt-pairs))))

;=======================================================
; Implements Findable/Target on common data structures 

(extend-protocol Findable
  ILookup
  (-select [this k not-found]
    (.valAt this k not-found))

  IPersistentVector
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

  IPersistentMap
  (-append [this k v]
    (.assoc this k v))

  IPersistentVector
  (-append [this k v]
    (mapv #(-append % k %2)
          (pad (count v) this nil)
          v)))

(ns robertluo.pullable.query
  (:import [clojure.lang IPersistentVector IPersistentMap IPersistentList
            ILookup Sequential IPersistentSet ExceptionInfo]))

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
  (-select [target k not-found]
    "returns value of target on k"))

(defprotocol Target
  (-append [target k v]
    "append value to target"))

(defn- default-transform
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

;;====================================
;; Options are wrapper of another query

(defrecord AsOption [query k]
  Query
  (-key [_] [k])
  (-value-of [_ m] (-value-of query m))
  (-transform [this target m]
    (default-transform this target m)))

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
      (-append target (-key this) v))))

(defrecord DataErrorOption [query ex-handler]
  Query
  (-key [_] (-key query))
  (-value-of [_ m]
    (try
      (-value-of query m)
      (catch ExceptionInfo ex
        (ex-handler ex))))
  (-transform [this target m]
    (default-transform this target m)))

(defn value-error [msg v]
  (ex-info msg (assoc {:error/kind :value} :value v)))

(defrecord SeqOption [query offset limit]
  Query
  (-key [_] (-key query))
  (-value-of [_ m]
    (let [v (-value-of query m)]
      (if (seqable? v)
        (cond->> v
          offset (drop offset)
          limit (take limit))
        (throw (value-error "value not seqable" v)))))
  (-transform [this target m]
    (default-transform this target m)))

(defrecord WithOption [query args]
  Query
  (-key [_] (-key query))
  (-value-of [_ m]
    (let [v (-value-of query m)]
      (if (fn? v)
        (apply v args)
        (throw (value-error "value is not a function" v)))))
  (-transform [this target m]
    (default-transform this target m)))

(defn pattern-error [msg arg]
  (ex-info msg (assoc {:error/kind :pattern} :arg arg)))

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
    (when (and offset (not (number? offset)))
      (throw (pattern-error "offset should be a number or nil" offset)))
    (when (and offset (not (number? limit)))
      (throw (pattern-error "limit should be a number or nil" limit)))
    (->SeqOption query offset limit)))

(defmethod create-option :with
  [{:option/keys [query arg]}]
  (when (not (vector? arg))
    (throw (pattern-error "with args should be a vector" arg)))
  (->WithOption query arg))

(defmethod create-option :exception
  [{:option/keys [query arg]}]
  (when (not (fn? arg))
    (throw (pattern-error "exception handler should be a function" arg)))
  (->DataErrorOption query arg))

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

  Sequential
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

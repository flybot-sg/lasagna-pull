(ns robertluo.pullable.core
  "Core namespace. consists of protocols, multimethods"
  (:import
   [clojure.lang IPersistentMap ILookup Sequential IPersistentSet
    APersistentVector]))

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
  :extend-via-metadata true
  (-select [target k]
    "returns value of target on k"))

(defprotocol Target
  "Sink of data"
  :extend-via-metadata true
  (-append [target k v]
    "append value to target"))

;;=======================
;; Query implementation

(defn default-transform
  [query target m]
  (-append target (-key query) (-value-of query m)))

(defrecord SimpleQuery [k]
  Query
  (-key [_] [k])
  (-value-of [_ m]
    (-select m k))
  (-transform [this target m]
    (default-transform this target m)))

(defn simple-query
  "Constructor for simple-query"
  ([k]
   (SimpleQuery. k)))

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
      (-value-of v-query v)))
  (-transform [this target m]
    (join-transform k-query v-query target m)))

(defn join-query
  "Constructor for join-query"
  ([k-q v-q]
   (JoinQuery. k-q v-q)))

(defrecord VectorQuery [queries]
  Query
  (-key [_] [(mapcat -key queries)])
  (-value-of [_ m]
    (map #(-value-of % m) queries))
  (-transform [this target m]
    (reduce (fn [t q] (-transform q t m))
            target
            queries)))

(defn vector-query
  "constructor of a vector query"
  ([queries]
   (VectorQuery. queries)))

(defmulti create-option
  "Create query option"
  :option/type)

;=======================================================
; Implements Findable/Target on common data structures

(extend-protocol Findable
  nil
  (-select [_ _]
    ::not-selectable)

  Object
  (-select [_ _]
    ::not-selectable)

  ;;handle the special case that joining on ::none
  clojure.lang.Keyword
  (-select [this _]
    (if (= this ::none)
      ::none-join
      ::not-selectable))

  ILookup
  (-select [this k]
    (.valAt this k ::none))

  ;;FIXME clojure's sequence has many different interfaces
  Sequential
  (-select [this k]
    (map #(-select % k) this))

  ;;FIXME a vector is also ILookup enabled, choose this sacrificed select
  ;;element based on index
  APersistentVector
  (-select [this k]
    (map #(-select % k) this))

  IPersistentSet
  (-select [this k]
    (map #(-select % k) this)))

(defn pad
  "returns a coll which has `n` length, with `coll` fill in first, then pad with
  value"
  [n coll value]
  (take n (concat coll (repeat value))))

(defn- seq-append
  ([coll k v]
   (seq-append (empty coll) coll k v))
  ([empty-coll coll k v]
   (when-not (coll? v)
     (throw (ex-info "impossible v" {:v v})))
   (into empty-coll
         (map (fn [x v] (if (= v ::none-join) ::none (-append x k v)))
              (pad (count v) coll nil)
              v))))

(defn- append [x k v]
  (if (not= v ::ignore)
    (assoc-in x k v)
    x))

(extend-protocol Target
  nil
  (-append [this k v]
    (append this k v))

  Object
  (-append [this k v]
    this)

  IPersistentMap
  (-append [this k v]
    (append this k v))

  Sequential
  (-append [this k v]
    (seq-append [] this k v))

  IPersistentSet
  (-append [this k v]
    (seq-append [] this k v)))

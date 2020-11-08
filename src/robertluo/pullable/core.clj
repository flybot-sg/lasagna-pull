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

(defn default-transform
  [query target m]
  (-append target (-key query) (-value-of query m)))

;;=======================
;; Query implementation

(defn request-join?
  [context m]
  (and
   (= :join (::type context))
   (some-> m meta :pull/request-join?)))

(defrecord SimpleQuery [context k]
  Query
  (-key [_] [k])
  (-value-of [_ m]
    (if (request-join? context m)
      ::request-join
      (-select m k)))
  (-transform [this target m]
    (default-transform this target m)))

(defn simple-query
  ([k]
   (simple-query nil k))
  ([context k]
   (SimpleQuery. context k)))

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

;=======================================================
; Implements Findable/Target on common data structures

(extend-protocol Findable
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
  [coll k v]
  (when-not (coll? v)
    (throw (ex-info "impossible v" {:v v})))
  (into (empty coll)
        (map #(-append % k %2)
             (pad (count v) coll nil)
             v)))

(def ^:const ignore :robertluo.pullable/ignore)

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

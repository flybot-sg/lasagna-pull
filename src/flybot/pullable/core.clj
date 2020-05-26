(ns flybot.pullable.core
  "Implementation of pulling")

(defprotocol Query
  "A query can return derived data from orignial data."
  (-select
   [q data]
   "select from data"))

;; If pulling encountered error, it will not throw exceptions, as this will break
;; potentially correct part of query. Instead of it, it will have an error data structure
;; in the value of this specific value.

(defrecord PullError [type info])

(defn error
  "returns an pulling error with type (keyword) and info (a maps)"
  [type info]
  (->PullError type info))

(defn select-merge
  [elem children]
  (->> (map #(-select % elem) children)
       (apply merge)))

;; Processor is an abstraction to allow further extending to support
;; more query options

(defprotocol Processor
  "A processor can process data with data and a sequence of children query"
  (-process
   [op data children]))

(defrecord CoreProcessor []
  Processor
  (-process
   [_ data children]
   (select-merge data children)))

(defrecord SeqProcessor []
  Processor
  (-process
   [_ data children]
   (if-let [data (seq data)]
     (map #(select-merge % children) data)
     (error ::unable-pull nil))))

(defrecord NotFoundProcessor [default]
  Processor
  (-process
   [_ data _]
   (if (= data ::not-found) default data)))

;;factory to create processors
;;
(defmulti option-create
  "Create a processor corresponding of a pair of k, v.
   Returns a pair of keyword, processor"
  first)

(defmethod option-create :default
  [_])

(defmethod option-create ::core
  [_]
  [::core (CoreProcessor.)])

(defmethod option-create :not-found
  [[_ v]]
  [::not-found (NotFoundProcessor. v)])

(defmethod option-create :seq?
  [[_ seq?]]
  (when seq?
    ;; a seq processor will replace default core processor
    [::core (SeqProcessor.)]))

(defn mk-processors
  "returns a creator array map for options kv"
  [options]
  (->> (map option-create (merge {::core true} options)) (into (array-map))))

;;=======================
;; Implemention of query
;; 
(defrecord CoreQuery [key children processors]
  Query
  (-select
    [_ data]
    (let [sub-data  (if key (get data key ::not-found) data)
          v         (if (seq children)
                      (reduce (fn [d proc] (-process proc d children)) sub-data (vals processors))
                      sub-data)]
      (if key {key v} v))))

(defn query
  "returns a query object for query spec, i.e. qspec,
   qspec is a mini DSL defined by k,v pair"
  ([qspec]
   (query qspec {}))
  ([qspec pspec]
   (map->CoreQuery (assoc qspec :processors (mk-processors pspec)))))

;;====================
;; Pattern

(defprotocol Queryable
  (-to-query
   [queryable]
   "returns a Query of itself"))

(extend-protocol Queryable
  nil
  (-to-query
    [_]
    (query {}))
  Object
  (-to-query
    [this]
    (query {:key this}))
  clojure.lang.PersistentVector
  (-to-query
   [this]
   (query {:children (map -to-query (.seq this))}))
  clojure.lang.IPersistentMap
  (-to-query
    [this]
    (if-let [[k v] (first this)]
      (let [processors (mk-processors (dissoc this k))]
        (assoc (-to-query v) :key k :processors processors))
      (-to-query nil))))

(defn pattern->query
  [ptn]
  (-to-query ptn))
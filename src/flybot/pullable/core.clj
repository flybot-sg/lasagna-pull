(ns flybot.pullable.core
  "Implementation of pulling")

;; Single method protocol, record vs high order function
;; - More readable, with documentation
;; - Has built in equalty, easier to test

(defprotocol Query
  "A query can return derived data from orignial data."
  (-select
   [q data]
   "select from data"))

(defprotocol Findable
  "Abstraction of getable data"
  :extend-via-metadata true
  (-get
   [findable k not-found]
   "Get value from findable for key k, if not found, returns not-found"))

;; Processor is an abstraction to process a query,
;; allow further extending to support
;; more query options

(defprotocol Processor
  "A processor can process data with data and a sequence of children query"
  (-process
   [op data children]))

;;=======================
;; Implemention of query
;; 

(defrecord CoreQuery [key children processors ex-handler]
  Query
  (-select
    [_ data]
    (let [v (try
              (let [sub-data (if key (-get data key ::not-found) data)]
                (reduce (fn [d proc] (-process proc d children))
                        sub-data
                        (vals processors)))
              (catch Exception ex
                (let [err #:error{:key key :message (ex-message ex)}]
                  ((or ex-handler identity) err))))]
      (if key {key v} v))))

(extend-protocol Findable
  clojure.lang.ILookup
  (-get [this k not-found]
    (.valAt this k not-found)))

(defn select-merge
  [elem children]
  (->> (map #(-select % elem) children)
       (apply merge)))

(defrecord CoreProcessor []
  Processor
  (-process
    [_ data children]
    (if (seq children)
      (select-merge data children)
      (when (not= data ::ignore)
        data))))

(defrecord SeqProcessor [offset limit]
  Processor
  (-process
    [_ data children]
    (cond 
      (map? data)      (throw (ex-info "Map is not considered as a seq" {:data data}))
      (not (seq data)) (throw (ex-info "Not a seq" {:data data}))
      :else            (cond->> (map #(select-merge % children) data)
                         offset (drop offset)
                         limit  (take limit)))))

(defrecord NotFoundProcessor [default]
  Processor
  (-process
    [_ data _]
    (if (= data ::not-found)
      default
      data)))

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

(defmethod option-create :seq
  [[_ s]]
  (let [[offset limit] s]
    [::core (SeqProcessor. offset limit)]))

(defn mk-processors
  "returns a creator array map for options kv"
  [options]
  (->> (map option-create (merge {::core true} options)) (into (array-map))))

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

(defn options->processors
  [options]
  (->> (partition 2 options)
       (map #(apply vector %))
       (into (array-map))
       (mk-processors)))

(defn expand-depth
  [depth m]
  (assert (pos? depth) "Depth must be a positive number")
  (let [[k v] (first m)
        d (let [v (dec depth)] (when (pos? v) v))
        v (conj v (cond-> (assoc m :not-found ::ignore) d (assoc :depth d)))]
    (assoc m k v)))

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
  clojure.lang.IPersistentList
  (-to-query
    [[v & options]]
    (let [processors (options->processors options)]
      (assoc (-to-query v) :processors processors)))
  clojure.lang.IPersistentMap
  (-to-query
   [this]
   (if-let [depth (:depth this)]
     ;;:depth is a special option, using rewrite to implement it
     (-to-query (expand-depth depth (dissoc this :depth)))
     (if-let [[k v] (first this)]
       (let [processors (mk-processors (dissoc this k))]
         (assoc (-to-query v) :key k :processors processors))
       (-to-query nil)))))

(defn pattern->query
  [ptn]
  (-to-query ptn))
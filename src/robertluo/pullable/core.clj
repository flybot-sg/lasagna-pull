(ns robertluo.pullable.core
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
  "A processor can process data after it processed"
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
                        processors))
              (catch Exception ex
                (let [err (merge (ex-data ex) #:error{:key key :message (ex-message ex)})]
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

(defn error [msg value]
  (throw (ex-info msg {:error/value value})))

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
      (map? data)      (error "Map is not considered as a seq" data)
      (not (seq data)) (error "Not a seq" data)
      :else            (cond->> (map #(select-merge % children) data)
                         offset (drop offset)
                         limit  (take limit)))))

(defrecord WithProcessor [args]
  Processor
  (-process
   [_ data _]
   (if (fn? data)
     (apply data args)
     (error "Not a function" data))))

(defrecord BatchProcessor [calls]
  Processor
  (-process [_ data children]
    (map #(-process (WithProcessor. %) data children) calls)))

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

(defmethod option-create :with
  [[_ args]]
  [::with (WithProcessor. args)])

(defmethod option-create :batch
  [[_ calls]]
  [::batch (BatchProcessor. calls)])

(defn query
  [qspec]
  (map->CoreQuery (assoc qspec
                         :children (->> (:children qspec)
                                        (map query))
                         :processors (->> (:options qspec)
                                          (cons [::core (CoreProcessor.)])
                                          (map option-create)
                                          (into (array-map))
                                          vals))))

; Copyright. 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.core
  "Implementation of queries.
   
   A query is a function which can extract k v from data.")

;;## Idea of acceptor
;; An acceptor is a function accepts k, v; it is the argument
;; of a query.
;; [:=> [:catn [:k :any] [:v :any]] :any]
;;
;; ### Why do we need acceptor?
;; A query do 2 things:
;;   - fetch value from data
;;   - returns a data
;; It defaults returning same data structure as its input.
;; However, if a query is a child of another query, it might want to have the 
;; resulting data in a shape which is different from the default one.
;; A query have a default acceptor function and can pass another acceptor
;; to override it.

(defn- val-acceptor
  "Identity acceptor, ignore k, only return `v`."
  [_ v]
  v)

(defn- accept
  "General acceptor function design: when k is nil, means there is no
   match, current progress should fail; when v is nil, means there is
   no value.
   
    - funcition `f-conj`, takes two argements, coll and a k-v pair
    - `x` is the collection willing to accept"
  [f-conj x k v]
  (cond
    (nil? k) nil
    (nil? v) x
    :else    (f-conj x [k v])))

(def ^:private sconj
  "commonn acceptor using `conj`"
  (partial accept conj))

(comment
  (sconj {} :a 1)
  (accept conj! (transient []) :a 1))

(def ^:private map-acceptor
  "common acceptor using an empty map to accept"
  (partial sconj {}))

;;## Query definition

(defprotocol DataQuery
  "Query"
  (-accept-data
   [query data f-accept]
   "accept data, f-accept is optional, to replace default acceptor"))

(defn query?
  "predict if `x` is a query"
  [x]
  (satisfies? DataQuery x))

(defn run-q
  "runs a query on `data` with optional function `f-accept`"
  ([q data]
   (run-q q data nil))
  ([q data f-accept]
   (-accept-data q data f-accept)))

;; Implementation of GenericQuery
;; - `ctx` data shared in a seri of queries
;; - `val-getter` is a function to fetch information from data
(defrecord GenericDataQuery [id val-getter acceptor ctx]
  DataQuery
  (-accept-data
   [_ data f-accept]
   (let [[k v] (val-getter data)]
     ((or f-accept acceptor) k v))))

(defn- pq
  "Construct a general query:  
    - `id` key of this query
    - `val-getter` is the function to extract data (k, v pair).
         [:=> [:catn [:kv [:any :any]]] [:any :any]]
    - `acceptor` default acceptor of this query, used when no acceptor specified when invoke.
         [:=> [:catn [:k :any] [:v :any]] :any]
   "
  ([id val-getter]
   (pq id val-getter nil))
  ([id val-getter acceptor]
   (pq id val-getter acceptor nil))
  ([id val-getter acceptor ctx]
   (->GenericDataQuery id val-getter acceptor ctx)))

(defn data-error!
  "Throws an exception specify data error."
  [reason k data]
  (throw (ex-info reason {:k k :data data})))

;; ### fn-query or simple query
;; Simplest form of a query
(defn fn-query
  "returns a simple query has key as `k`, `f` to return a value from a map."
  ([k]
   (fn-query k nil))
  ([k ctx]
   (fn-query k #(get % k) ctx))
  ([k f ctx]
   (pq k
       (fn [data]
         (when-not (associative? data)
           (data-error! "Associative(map) required" k data))
         [k (f data)])
       map-acceptor
       ctx)))

(comment
  (run-q (fn-query :a) {:a 3}))

;;### join-query
;; Joins two queries altogether

(defn join-query
  "returns a joined query: `q` queries in `parent-q`'s returned value."
  ([parent-q q]
   (join-query parent-q q nil))
  ([parent-q q ctx]
   (let [qid (:id parent-q)]
     (pq qid 
         (fn [data]
           [qid (when-let [pd (run-q parent-q data val-acceptor)]
                  (run-q q pd))])
         (:acceptor parent-q)
         ctx))))

(comment
  (run-q (join-query (fn-query :a) (fn-query :b)) {:a {:b 1}})
  )

;; ### vector-query
;; A vector query is a query collection as `queries`
(defn vector-query
  "returns a vector query, takes other `queries` as its children,
   then apply them to data, return the merged map."
  ([queries]
   (vector-query queries nil))
  ([queries ctx]
   (let [qid (map :id queries)]
     (pq qid
         (fn [data]
           (let [collector (transient [])
                 v         (some->> queries
                                    (reduce (fn [acc q]
                                              (run-q q
                                                     data
                                                     (comp
                                                      #(if (nil? %) (reduced nil)  %)
                                                      (partial accept conj! acc))))
                                            collector)
                                    (persistent!))]
             [qid (into {} v)]))
         val-acceptor
         ctx))))

(comment
  (run-q (vector-query [(fn-query :a) (fn-query :b)]) {:a 3 :b 5 :c 7}))

;;### filter query
(defn filter-query
  "Returns a filter query which will not appears in result data, but will
   void other query if in a vector query, `pred` is the filter condition.
   It does not interact with context."
  ([q pred]
   (filter-query q pred nil))
  ([q pred ctx]
   (pq (:id q)
       (fn [data]
         (let [[k v] (run-q q data vector)]
           [(when (pred v) k) nil]))
       (:acceptor q)
       ctx)))

(comment
  (run-q (filter-query (fn-query :a) odd?) {:a 2}))

;;### seq query
;; A SeqQuery can apply to a sequence of maps
(defn seq-query
  "Returns a seq-query which applies query `q` to data (must be a collection) and return."
  ([q]
   (seq-query q nil))
  ([q ctx]
   (let [qid (:id q)]
     (pq qid
         (fn [data]
           (when-not (sequential? data)
             (data-error! "Sequential data required" qid data))
           [qid (map #(run-q q %) data)])
         val-acceptor
         ctx))))

(comment
  (run-q (seq-query (vector-query [(fn-query :a) (fn-query :b)])) [{:a 3 :b 4} {:a 5} {}]))

;;### post-process-query
;; It is common to process data after it matches.
(defn post-process-query
  "A query decorator post process its result. Given query `child`, the function
   `f-post-process` may change its value and return.
      - child: a query
      - f-post-process: [:=> [:kv-pair] :any]"
  ([child f-post-process]
   (post-process-query child f-post-process nil))
  ([child f-post-process ctx]
   (let [qid (:id child)]
     (pq qid
         (fn [data]
           [qid (some-> (run-q child data vector) (f-post-process) (second))])
         (:acceptor child)
         ctx))))

;;### Logical variable support

(defprotocol OutputQueryContext
  "a context support variable binding output"
  (-named-query
    [ctx sym q]
    "returns a named query for `sym`")
  (-bindings
   [ctx]
   "returns the output bindings"))

(defn- bind-kv
  [symbol-table sym v]
  (let [old-val (get @symbol-table sym ::not-found)] 
    (condp = old-val
      ::not-found (do (swap! symbol-table assoc sym v) v)
      v v
      (do (swap! symbol-table dissoc sym) nil))))

(deftype QueryContextImpl [symbols]
  OutputQueryContext
  (-named-query
    [this sym q]
    (pq (:id q)
        (fn [data]
          (let [[k v] (run-q q data vector)
                v     (bind-kv symbols sym v)]
            [(when v k) v]))
        (:acceptor q)
        this))
  (-bindings
   [_]
   (deref symbols)))

(defn context 
  "returns a new query context"
  []
  (->QueryContextImpl (atom {})))

(defn run-bind
  "`f-query` takes a function (returned by `named-var-factory`) as argument,
  returns a query, then run it on `data`, finally returns a vector:
    - query result
    - named variable binding map"
  [f-query data]
  (let [data (run-q f-query data)]
    [data (some-> f-query :ctx (-bindings))]))

(comment
  (run-bind (-named-query (context) 'a (fn-query :a)) {:a 2})
  )

;;## Post processors

;; Post processors apply after a query, abbreciate to `pp`
(defmulti apply-post
  "create a post processor by ::pp-type, returns a function takes
   k-v pair, returns the same shape of data"
  :proc/type)

(defn assert-arg!
  "An error that represent apply-post illegal argument."
  [pred arg]
  (when-not (pred (:proc/val arg))
    (throw (ex-info "illegal argument" arg))))

(defmethod apply-post :default
  [arg]
  (assert-arg! (constantly false) arg))

(defn decorate-query
  "Returns a query which is `q` decorated by options specified by `pp-pairs`."
  ([q pp-pairs]
   (decorate-query q pp-pairs nil))
  ([q pp-pairs ctx]
   (reduce
    (fn [acc [pp-type pp-value]]
      (post-process-query
       acc
       (apply-post #:proc{:type pp-type :val pp-value})
       ctx))
    q pp-pairs)))

;;### :when option
;; Takes a pred function as its argument (:proc/val)
;; when the return value not fullfil `pred`, it is not included in result.
(defmethod apply-post :when
  [arg]
  (let [{pred :proc/val} arg]
    (assert-arg! fn? arg)
    (fn [[k v]]
      [k (when (pred v) v)])))

;;### :not-found option
;; Takes any value as its argument (:proc/val)
;; When a value not found, it gets replaced by not-found value
(defmethod apply-post :not-found
  [{:proc/keys [val]}]
  (fn [[k v]]
    [k (or v val)]))

;;### :with option
;; Takes a vector of args as this option's argument (:proc/val)
;; Requires value being a function, it applies the vector of args to it,
;; returns the return value as query result.
(defmethod apply-post :with
  [arg]
  (let [{args :proc/val} arg]
    (assert-arg! vector? arg)
    (fn [[k f]]
      (when-not (fn? f)
        (data-error! "value must be a function" k f))
      [k (apply f args)])))

;;### :batch option
;; Takes a vector of vector of args as this options's argument. 
;; Applible only for function value.
;; query result will have a value of a vector of applying resturn value.
(defmethod apply-post :batch
  [arg]
  (assert-arg! #(and (vector? %) (every? vector? %)) arg)
  (let [{args-vec :proc/val} arg]
    (fn [[k f]]
      (when-not (fn? f)
        (data-error! "value must be a function" k f))
      [k (map #(apply f %) args-vec)])))

;;### :seq option (Pagination)
;; Takes a pair of numbers as this option's argument.
;;  [:catn [:from :number] [:count :number]]
;; Appliable only for seq query.
;; query result has a value of a sequence of truncated sequence.
(defmethod apply-post :seq
  [arg]
  (assert-arg! vector? arg)
  (let [[from cnt] (:proc/val arg)
        from       (or from 0)
        cnt        (or cnt 0)]
    (fn [[k v]]
      (when-not (seqable? v)
        (data-error! "seq option can only be used on sequences" k v))
      [k (->> v (drop from) (take cnt))])))

;;### :watch option
;; Takes an function as the argument (:proc/val): 
;;    [:=> [:catn [:old-value :any] [:new-value :any]] :any]
;; returns `nil` when your do want to watch it anymore.
;; Can watch on a IRef value
(def watchable?
  "pred if `x` is watchable"
  (partial instance? clojure.lang.IRef))

(defmethod apply-post :watch
  [arg]
  (assert-arg! fn? arg)
  (let [f       (:proc/val arg)
        w-k     ::watch
        watcher (fn [_ watched old-value new-value]
                  (when (nil? (f old-value new-value))
                    (remove-watch watched w-k)))]
    (fn [[k v]]
      (when-not (watchable? v)
        (data-error! "watch option can only apply to an watchable value" k v))
      (add-watch v w-k watcher)
      [k @v])))

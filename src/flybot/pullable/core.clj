; Copyright. 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns flybot.pullable.core
  "Implementation of queries.
   
   A query is a function which can extract k v from data.")

;;## Idea of acceptor
;; An acceptor is a function accepts k, v; it is the argument
;; of a query.
;; [:=> [:catn [:k :any] [:v :any]] :any]
;;
;; ### Why do we need acceptor?
;; If a query is in a parent query context, it might want to have the 
;; resulting data in a shape which is different from the default 
;; context. Passing a acceptor function to a query can do it. If not,
;; a query uses its default acceptor function.

(defn val-acceptor
  "Identity acceptor, ignore k, only return `v`."
  [_ v]
  v)

(defn accept
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

(def sconj
  "commonn acceptor using `conj`"
  (partial accept conj))

(comment
  (sconj {} :a 1)
  (accept conj! (transient []) :a 1))

(def map-acceptor
  "common acceptor using an empty map to accept"
  (partial sconj {}))

;;## Query definition

(defn pq
  "Construct a general query:  
    - `id` key of this query
    - `acceptor` default acceptor of this query, used when no acceptor specified when invoke.
         [:=> [:catn [:k :any] [:v :any]] :any]
    - `val-getter` is the function to extract data (k, v pair).
         [:=> [:catn [:kv [:any :any]]] [:any :any]]
   "
  [id acceptor val-getter]
  (with-meta
    (fn accept-data 
      ([data]
       (accept-data nil data))
      ([accept data]
       (apply (or accept acceptor) (val-getter data))))
    {::id id ::acceptor acceptor}))

(defn id 
  "Returns id of a query."
  [query]
  (some-> query meta ::id))

(defn acceptor
  "Returns default acceptor of a query."
  [query]
  (some-> query meta ::acceptor))

(defn data-error!
  "Throws an exception specify data error."
  [reason k data]
  (throw (ex-info reason {:k k :data data})))

;; ### fn-query or simple query
;; Simplest form of a query
;;FIXME stack consuming
(defn fn-query
  "Returns a simple query has key as `k`, `f` to return a value from a map."
  ([k]
   (fn-query k #(get % k)))
  ([k f]
   (pq k map-acceptor
       (fn [data]
         (when-not (associative? data)
           (data-error! "associative(map) expected" k data))
         [k (f data)]))))

(comment
  ((fn-query :a) {:a 3}))

;;### join-query
;; Joins two queries altogether

(defn join-query
  "Returns a joined query: `q` queries in `parent-q`'s returned value."
  [parent-q q]
  (let [qid (id parent-q)]
    (pq qid (acceptor parent-q)
        (fn [data]
          [qid (some-> (parent-q val-acceptor data) q)]))))

(comment
  ((join-query (fn-query :a) (fn-query :b)) {:c 3})
  )

;; ### vector-query
;; A vector query is a query collection as `queries`
(defn vector-query
  "Returns a vector query, takes other `queries` as its children,
   then apply them to data, return the merged map."
  [queries]
  (let [qid (map id queries)]
    (pq qid val-acceptor
        (fn [data]
          (let [collector (transient [])
                v (some->> queries
                           (reduce (fn [acc q]
                                     (q
                                      (comp
                                       #(if (nil? %) (reduced nil)  %)
                                       (partial accept conj! acc))
                                      data))
                                   collector)
                           (persistent!))]
            [qid (into {} v)])))))

(comment
  ((vector-query [(fn-query :a) (fn-query :b)]) {:a 3 :b 5 :c 7}))

;;### filter query
(defn filter-query
  "Returns a filter query which will not appears in result data, but will
   void other query if in a vector query, `pred` is the filter condition."
  [q pred]
  (pq (id q) (acceptor q)
      (fn [data]
        (let [[k v] (q vector data)]
          [(when (pred v) k) nil]))))

(comment
  ((filter-query (fn-query :a) odd?) {:a 1}))

;;### seq query
;; A SeqQuery can apply to a sequence of maps
(defn seq-query
  "Returns a seq-query which applies query `q` to data (must be a collection) and return."
  [q]
  (let [qid (id q)]
    (pq qid val-acceptor
        (fn [data]
          (when-not (sequential? data)
            (data-error! "sequence expected" nil data))
          [qid (map q data)]))))

(comment
  ((seq-query (vector-query [(fn-query :a) (fn-query :b)])) [{:a 3 :b 4} {:a 5} {}]))

;;### post-process-query
;; It is common to process data after it matches.
(defn post-process-query
  "A query decorator post process its result. Given query `child`, the function
   `f-post-process` may change its value and return.
      - child: a query
      - f-post-process: [:=> [:kv-pair] :any]"
  [child f-post-process]
  (let [qid (id child)]
    (pq qid (acceptor child)
        (fn [data]
          [qid (some-> (child vector data) (f-post-process) (second))]))))

;;### Logical variable support
(defn mk-named-var-query
  "Returns a factory function which take a query `q` as its argument."
  ([t-sym-table sym]
   (mk-named-var-query t-sym-table (atom :fresh) sym))
  ([t-sym-table status sym]
   (fn [q]
     (pq
      (id q)
      (acceptor q)
      (fn [data]
        (let [[k v] (q vector data)]
          (case @status
            :fresh
            (do
              (assoc! t-sym-table sym v)
              (reset! status :bound))

            :bound
            (let [old-v (get t-sym-table sym ::not-found)]
              (when (not= v old-v)
                (dissoc! t-sym-table sym)
                (reset! status :invalid)))
            nil)
          [(when (not= @status :invalid) k) v]))))))

(comment
  (let [a-sym-table (transient {})]
    [(((mk-named-var-query a-sym-table '?a) (fn-query :a)) {:a 3})
     (persistent! a-sym-table)]))

(defn named-var-factory
  "Returns a function takes a symbol as the argument, returns a named-var-query."
  []
  (let [t-sym-table (transient {})
        ;;cache for created named variable factory
        a-cx-named  (atom {})]
    [#(persistent! t-sym-table)
     (fn [sym]
       (or (get @a-cx-named sym)
           (let [qf (mk-named-var-query t-sym-table sym)]
             (swap! a-cx-named assoc sym qf)
             qf)))]))

(defn run-bind
  "`f-query` takes a function (returned by `named-var-factory`) as argument,
  returns a query, then run it on `data`, finally returns a vector:
    - query result
    - named variable binding map"
  [f-query data]
  (let [[f-sym-table f-named-var] (named-var-factory)]
    [((f-query f-named-var) data) (f-sym-table)]))

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
  [q pp-pairs]
  (reduce
   (fn [acc [pp-type pp-value]]
     (post-process-query
      acc
      (apply-post #:proc{:type pp-type :val pp-value})))
   q pp-pairs))

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

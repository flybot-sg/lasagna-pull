(ns robertluo.pullable.core
  "Implementation of query")

;;== Query implementations

(defn term-query
  "identity query, "
  ([v]
   (constantly (constantly v))))

(defn val-acceptor
  "identity acceptor, ignore k, only return `v`"
  [_ v]
  v)

(defrecord PullQuery [id acceptor val-getter]
  clojure.lang.IFn
  (invoke
    [_ accept]
    ((or accept acceptor) id (val-getter))))

(def q ->PullQuery)

(defn run-query
  "run a query `q` on `data`, returns the query result function.
   
   A query result function has one argument of acceptor, which is
   another function takes k, v as arguments. The acceptor can be nil,
   means that the query are free to construct the result."
  [q data]
  ((q data) nil))

(comment
  (run-query (term-query {:a 3}) {}))

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

(defn data-error
  "throw an exception specify data error"
  [reason k data]
  (throw (ex-info reason {:k k :data data})))

;;FIXME stack consuming
(defn fn-query
  "returns a simple query has key as `k`, `f` to return a value from a map,
   and `child` query to execute if matches."
  ([k]
   (fn-query k #(get % k)))
  ([k f]
   (fn-query k f nil))
  ([k f child]
   (fn [data]
     (when-not (associative? data)
       (data-error "associative(map) expected" k data))
     (q k map-acceptor
        #(let [v (f data)]
           (when-not (nil? v)
             (let [child-q (or child (term-query v))]
               ((child-q v) nil))))))))

(comment
  (run-query (fn-query :a) {:a 3}))

;; A vector query is a query collection as `queries`
(defn vector-query
  "returns a vector query, takes other `queries` as its children,
   then apply them to data, return the merged map."
  [queries]
  (fn [data]
    (let [collector (transient [])
          v (some->> queries
                     (reduce (fn [acc q]
                               ((q data)
                                (comp
                                 #(if (nil? %) (reduced nil)  %)
                                 (partial accept conj! acc))))
                             collector)
                     (persistent!))]
      (q (map first v) val-acceptor #(into {} v)))))

(comment
  (run-query (vector-query [(fn-query :a) (fn-query :b)]) {:a 3 :b 5 :c 7}))

(defn post-process-query
  "A query decorator post process its result"
  [child f-post-process]
  (fn [data]
    (let [child-q (child data)
          [k v]   (some-> (child-q vector) (f-post-process))]
      (q k (:acceptor child-q) (fn [] v)))))

(defn filter-query
  "returns a filter query which will not appears in result data, but will
   void other query if in a vector query, `pred` is the filter condition"
  [q pred]
  (let [pred (if (fn? pred) pred #(= pred %))]
    (post-process-query q (fn [[k v]] [(when (pred v) k) nil]))))

(comment
  (run-query (filter-query (fn-query :a) odd?) {:a 2}))

;; A SeqQuery can apply to a sequence of maps
(defn seq-query
  "returns a seq-query which applies query `q` to data (must be a collection) and return"
  ([qr]
   (fn [data]
     (when-not (sequential? data)
       (data-error "sequence expected" nil data))
     (let [v (map #((qr %) nil) data)]
       (q (:id qr) val-acceptor (fn [] v))))))

(comment
  (run-query (seq-query (vector-query [(fn-query :a) (fn-query :b)])) [{:a 3 :b 4} {:a 5} {}]))

(defn mk-named-var-query
  "returns a factory function which take a query `q` as its argument."
  ([t-sym-table sym]
   (mk-named-var-query t-sym-table (atom :fresh) sym))
  ([t-sym-table status sym]
   (fn [qr]
     (post-process-query
      qr
      (fn [[k v]]
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
        [(when (not= @status :invalid) k) v])))))

(comment
  (let [a-sym-table (transient {})]
    [(run-query ((mk-named-var-query a-sym-table '?a) (fn-query :a)) {:a 3})
     (persistent! a-sym-table)]))

(defn named-var-factory
  "returns a function takes a symbol as the argument, returns a named-var-query"
  []
  (let [t-sym-table (transient {})
        ;;cache for created named variable factory
        a-cx-named  (atom {})]
    [#(persistent! t-sym-table)
     (fn [sym]
       (or (get @a-cx-named sym)
           (mk-named-var-query t-sym-table sym)))]))

(defn run-bind
  "`f-query` takes a function (returned by `named-var-factory`) as argument,
  returns a query, then run it on `data`, finally returns a vector:
    - query result
    - named variable binding map"
  [f-query data]
  (let [[f-sym-table f-named-var] (named-var-factory)]
    [(run-query (f-query f-named-var) data) (f-sym-table)]))

;;== post processors

;; Post processors apply after a query, abbreciate to `pp`
(defmulti apply-post
  "create a post processor by ::pp-type, returns a function takes
   k-v pair, returns the same shape of data"
  :proc/type)

(defmethod apply-post :default
  [_]
  identity)

(defn decorate-query
  [q pp-pairs]
  (reduce
   (fn [acc [pp-type pp-value]]
     (post-process-query
      acc
      (apply-post #:proc{:type pp-type :val pp-value})))
   q pp-pairs))

(defmethod apply-post :when
  [{:proc/keys [val]}]
  (fn [[k v]]
    [k (when (val v) v)]))

(defmethod apply-post :not-found
  [{:proc/keys [val]}]
  (fn [[k v]]
    [k (or v val)]))

(defmethod apply-post :with
  [{:proc/keys [val]}]
  (fn [[k v]]
    (when-not (fn? v)
      (data-error ":with option need a function" k v))
    [k (apply v val)]))

(defmethod apply-post :seq
  [{:proc/keys [val]}]
  (when-not (vector? val)
    (throw (ex-info "seq option's arugment must be a vector" {:v val})))
  (let [[from cnt] val
        from       (or from 0)
        cnt        (or cnt 0)]
    (fn [[k v]]
      (when-not (seqable? v)
        (data-error "seq option can only be used on sequences" k v))
      [k (->> v (drop from) (take cnt))])))

;;TODO batch option

(ns robertluo.pullable.query
  "The core construct of queries.
   A query is a continuation function which take abitary data as its argument,
   returns a derived data structure.
   
   Continuation means it takes the next step as an argument, each query
   will call its next step. This is a natural way to express join.")

(defn fn-query
  "Returns a query that takes single-arity function `f`,
   it will assoc to the result data with key by apply `kf` to `f`,
   and `f data` as the value"
  ([f]
   (fn-query f identity identity))
  ([f next-fn post-fn]
   (fn [data]
     (let [v (some-> data f next-fn)
           [k v] (post-fn [f v])]
       (cond-> {}
         (not (nil? v)) (assoc k v))))))

(comment
  ((fn-query :a) {:a 3 :b 4})
  ((fn-query :a (fn-query :b) identity) {:a {:b 3 :c 4}})
  )

(defn vector-query
  "A query that takes multiple queries as childen, apply them
   seperatedly on data, then merge the result together."
  ([children]
   (vector-query children identity identity))
  ([children next-fn post-fn]
   (fn [data]
     (let [v (map #(some-> data % next-fn) children)
           [_ v] (post-fn [children v])]
       (apply merge v)))))

(comment
  ((vector-query [(fn-query :a) (fn-query :b)]) {:a 3 :b 4 :c 5})
  ((fn-query :a 
             (vector-query
              [(fn-query :b)
               (fn-query :c)]) identity)
   {:a {:b 2 :c 3 :d 4} :e 5})
  )

(defn seq-query
  "Decorate `query` with the ablitity to automatically distinguish a sequence and
   a normal value, for a sequence, it will map `query` on data."
  ([query]
   (seq-query query identity identity))
  ([query next-fn post-fn]
   (fn [data]
     (when-not (seqable? data) (throw (ex-info "Data must be seqable" {:v data})))
     (let [result (map #(-> % query next-fn) data)]
       (post-fn result)))))

(comment
  ((seq-query (fn-query :a)) [{:a 3 :b 5} {:a 4} {:b 3}])
  )

(defmulti mk-post-fn 
  "returns a `post-fn` for post-procession query result k,v pair"
  (fn [x _] x))
(defmethod mk-post-fn :default [_ _] identity)

(defn as-query
  "Construct query from pull expression x."
  ([x]
   (cond
     (vector? x)
     (-> (map as-query x)
         (vector-query))

     (map? x)
     (-> (map (fn [[k v]] (fn-query k (as-query v) identity)) x)
         (vector-query))

     (list? x)
     (-> (map #(seq-query (as-query %)) x)
         (vector-query))

     (ifn? x)
     (fn-query x)

     :else
     (throw (ex-info "Unable to be a query" {:x x})))))

(defn run
  "run pull expression `x` on `data`, returns pull result."
  [x data]
  ((as-query x) data))

(comment
  (run {:a [:b]} {:a {:b 3 :c 4}})
  )

(defmethod mk-post-fn :as
  [_ kf]
  (let [kf (if (fn? kf) kf (constantly kf))]
    (fn [[k v]]
      [(kf k) v])))

(comment
  ((mk-post-fn :as "a") [:a 3])
  ((mk-post-fn :as str) [:a 3])
  )

(defmethod mk-post-fn :not-found
  [_ not-found]
  (fn [[k v]]
    [k (if (nil? v) not-found v)]))

(comment
  ((mk-post-fn :not-found ::ok) [:a nil])
  )

(defmethod mk-post-fn :with
  [_ args]
  (fn [[k v]]
    (when-not (fn? v)
      (throw (ex-info ":with can only apply to a function value" {:v v})))
    [k (apply v args)]))

(comment
  ((mk-post-fn :with [2]) [:a inc]))

(defmethod mk-post-fn :seq
  [_ [offset cnt]]
  (assert (and (pos? offset) (pos? cnt)))
  (fn [[k v]]
    (when-not (seqable? v)
      (throw (ex-info ":seq can only apply to sequence" {:v v})))
    [k (->> v (drop offset) (take cnt))]))

(comment
  ((mk-post-fn :seq [2 3]) [:a (range 10)])
  )
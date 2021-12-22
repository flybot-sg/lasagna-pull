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
   (fn-query identity f identity))
  ([next-fn f post-fn]
   (fn [data]
     (let [v (-> data f next-fn)
           [k v] (post-fn [f v])]
       (cond-> {}
        (not (nil? v)) (assoc k v))))))

(comment
  ((fn-query :a) {:a 3 :b 4})
  ((fn-query (fn-query :b) :a identity) {:a {:b 3 :c 4}})
  )

(defn vector-query
  "A query that takes multiple queries as childen, apply them
   seperatedly on data, then merge the result together."
  ([children]
   (vector-query identity children))
  ([next-fn children]
   (fn [data]
     (->> (map #(-> data % next-fn) children)
          (apply merge)))))

(comment
  ((vector-query [(fn-query :a) (fn-query :b)]) {:a 3 :b 4 :c 5})
  ((fn-query (vector-query
              [(fn-query :b)
               (fn-query :c)]) :a identity)
   {:a {:b 2 :c 3 :d 4} :e 5})
  )

(defn seq-decorator
  "Decorate `query` with the ablitity to automatically distinguish a sequence and
   a normal value, for a sequence, it will map `query` on data."
  [query]
  (fn [data]
    (cond
      (sequential? data) (map query data)
      :else (query data))))

(comment
  ((seq-decorator (fn-query :a)) [{:a 3 :b 5} {:a 4} {:b 3}])
  )

(defmulti mk-post-fn (fn [x _] x))
(defmethod mk-post-fn :default [_ _] identity)

(defn as-query
  "Construct query from pull expression x."
  ([next-fn x post-fn]
   (let [fn-query (comp seq-decorator fn-query)
         vector-query (comp seq-decorator vector-query)]
     (cond
       (vector? x)
       (->> (map #(as-query identity % post-fn) x)
            (vector-query next-fn))

       (map? x)
       (->> (map (fn [[k v]] (as-query (as-query identity v identity) k post-fn)) x)
            (vector-query next-fn))

       (list? x)
       #dbg
       (let [[q & options] x]
         (assert q "First item in list must be a query")
         (let [post-fn (reduce (fn [f [ok ov]]
                                 (comp (mk-post-fn ok ov) f))
                               identity
                               (partition 2 options))]
           (as-query next-fn q post-fn)))

       (ifn? x)
       (fn-query next-fn x post-fn)

       :else
       (throw (ex-info "Unable to be a query" {:x x}))))))

(defn run
  "run pull expression `x` on `data`, returns pull result."
  [x data]
  ((as-query identity x identity) data))

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
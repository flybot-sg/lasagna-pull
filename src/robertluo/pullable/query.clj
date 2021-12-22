(ns robertluo.pullable.query
  "The core construct of queries")

(defn fn-query
  "A query that takes a single argument function f"
  [next-fn f]
  (fn [data]
    (assoc (empty data) f (-> data f next-fn))))

(comment
  ((fn-query identity :a) {:a 3 :b 4})
  ((fn-query (fn-query identity :b) :a) {:a {:b 3 :c 4}})
  )

(defn vector-query
  "A query that takes multiple queries as childen, apply them
   seperatedly on data, then merge the result together"
  [next-fn children]
  (fn [data]
    (->> (map #(-> data % next-fn) children)
         (apply merge))))

(comment
  ((vector-query identity [(fn-query identity :a) (fn-query identity :b)]) {:a 3 :b 4 :c 5})
  ((fn-query (vector-query identity
                           [(fn-query identity :b)
                            (fn-query identity :c)]) :a)
   {:a {:b 2 :c 3 :d 4} :e 5})
  )

(defn seq-decorator
  [query]
  (fn [data]
    (cond
      (map? data) (query data query)
      (sequential? data) (map query data)
      :else (throw (ex-info "Wrong data shape" {:data data})))))

(comment
  ((seq-decorator (fn-query identity :a)) [{:a 3 :b 5} {:a 4} {:b 3}])
  )

(defn as-query
  ([x]
   (as-query identity x))
  ([next-fn x]
   (cond
     (vector? x)
     (->> (map as-query x)
          (vector-query next-fn))

     (map? x)
     (->> (map (fn [[k v]] (as-query (as-query v) k)) x)
          (vector-query next-fn))

     (ifn? x)
     (fn-query next-fn x)

     :else
     (throw (ex-info "Unable to be a query" {:x x})))))

(comment
  ((as-query [:a :b]) {:a 4 :b 5 :c 3})
  ((as-query :a) {:a 5})
  ((as-query {:a [:b]}) {:a {:b 3 :c 4}})
  )


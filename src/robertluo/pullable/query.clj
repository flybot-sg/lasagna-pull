(ns robertluo.pullable.query)

(defn simple-query [kw]
  (fn [data]
    (let [value (kw data)]
      [#(assoc (empty data) % %2) kw value])))

(defn run-query [q data]
  (let [[f k v] (q data)]
    (f k v)))

(comment
  (run-query (simple-query :a) {:a 3})
  )

(defn join-query 
  [key-query value-query]
  (fn [data]
    (let [[f-k k-k v-k] (key-query data)
          [f-v k-v v-v] (value-query v-k)
          val (f-v k-v v-v)]
      [f-k k-k val])))

(comment
  (run-query (join-query (simple-query :a) (simple-query :b)) {:a {:b 3 :c 5}}))

(defn vector-query [& children]
  (fn [data]
    [#(apply merge %2) children (map #(run-query % data) children)]))

(comment
  (run-query (vector-query (simple-query :a) (simple-query :b)) {:a 3 :b 4})
  (run-query (join-query (simple-query :a)
                         (vector-query (simple-query :b)
                                       (simple-query :c)))
             {:a {:b 3 :c 5 :d 7}}))

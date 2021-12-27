(ns robertluo.pullable.query
  "The core construct of queries.
   A query is a continuation function which take abitary data as its argument,
   returns a derived data structure.
   
   Continuation means it takes the next step as an argument, each query
   will call its next step. This is a natural way to express join.")

(def IGNORE
  "Ignore value"
  :pull/ignore)

(defprotocol Query
  (-key [query] "returns the key of the `query`")
  (-value [query data] "returns the value on `data`")
  (-matching [query data] "returns the matching result of `data`"))

(defn matches [query data]
  (let [v (-value query data)]
    (cond-> {}
      (not= IGNORE v) (assoc (-key query) v))))

(defrecord SimpleQuery [k f child]
  Query
  (-key [_] k)
  (-value
    [_ data]
    (let [v (f data)]
      (if (= v IGNORE)
        IGNORE
        (if child
          (-matching child v)
          v))))
  (-matching
   [this data]
   (matches this data)))

(defn simple-query
  ([k]
   (simple-query k #(get % k IGNORE)))
  ([k f]
   (simple-query k f nil))
  ([k f child]
   (->SimpleQuery k f child)))

(defrecord NotFoundQuery [q not-found]
  Query
  (-key [_] (-key q))
  (-value
   [_ data]
   (let [v (-value q data)]
     (if (= IGNORE v)
       not-found
       v)))
  (-matching 
   [this data]
   (matches this data)))

(def not-found-query ->NotFoundQuery)

(defrecord VectorQuery [queries]
  Query
  (-key [_] (map -key queries))
  (-value
   [_ data]
   (map #(-value % data) queries))
  (-matching
   [this data]
   (into {} (map vector (map -key queries) (-value this data)))))

(def vector-query ->VectorQuery)

(defrecord SeqQuery [q]
  Query
  (-key [_] (-key q))
  (-value
   [_ data]
   (map #(-value q %) data))
  (-matching
   [_ data]
   (map #(-matching q %) data)))

(def seq-query ->SeqQuery)

(def matching -matching)

(comment
  (matching (simple-query :a) {:a 3 :b 4})
  (matching (simple-query :a :a (simple-query :b)) {:a {:b 3 :c 4}})
  (matching (not-found-query (simple-query :a) ::ok) {})
  (matching (vector-query [(simple-query :a) (simple-query :b)]) {:a 3 :c 5})
  (matching (seq-query (vector-query [(simple-query :a) (simple-query :b )])) [{:a 3 :b 4} {:a 5} {}])
  )

(defn error
  [msg data]
  (throw (ex-info msg data)))

(defn ->query
  [x]
  (cond
    (map? x)
    (vector-query (for [[k v] x] (if (= v '?) (simple-query k) (simple-query k k (->query v)))))
    
    (vector? x)
    (seq-query (->query (first x)))
    
    :else
    (error "Not able to construct query" {:x x})))

(defn run
  [x data]
  (-matching (->query x) data))

(comment
  (->query '{:a ? :b ?})
  (->query '{:a {:b ?}})
  (->query '[{:a ?}])
  (run '{:a ? :b ?} {:a 3 :b 4 :c 5})
  (run '{:a {:b ?}} {:a {:b 3 :c 5}})
  (run '[{:a ?}] [{:a 3 :b 4} {}])
  )
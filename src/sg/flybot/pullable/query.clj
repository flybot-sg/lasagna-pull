(ns sg.flybot.pullable.query)

(defprotocol Acceptor
  "An acceptor receives information"
  (-accept
    [dc k v]
    "accept k,v pair to the context, returns resulting data"))

(defprotocol DataQuery
  "Query"
  (-id
    [query]
    "returns the id (a.k.a key) of the query")
  (-default-acceptor
    [query]
    "return the default context if the query is running independently")
  (-run
    [query data acceptor]
    "run `query` based on `data`, using `acceptor` to accept"))

(extend-protocol Acceptor
  nil
  (-accept [_ _ _] nil))

(defn run-query
  "runs a query `q` on `data` using `acceptor` (when nil uses q's default acceptor)"
  ([q data]
   (run-query q data nil))
  ([q data acceptor]
   (-run q data (or acceptor (-default-acceptor q)))))

(defn map-acceptor
  "an acceptor of a map `m`"
  [m]
  (reify Acceptor
    (-accept [_ k v]
      (cond
        (nil? k) nil
        (nil? v) m
        :else
        (assoc m k v)))))

(defn fn-query
  "a query using a function to extract data
   - `k` the id of the query
   - `f` function takes data as its argument, returns extracted data"
  ([k]
   (fn-query k #(get % k)))
  ([k f]
   (reify DataQuery
     (-id [_] k)
     (-default-acceptor [_] (map-acceptor {}))
     (-run [_ data acceptor]
       (-accept acceptor k (f data))))))

(comment
  (run-query (fn-query :a) {:b 3}))

(defn value-acceptor
  "returns an acceptor of value only"
  []
  (reify Acceptor
    (-accept [_ _ v] v)))

(defn join-query
  "returns a joined query of two queries
    - `parent` is a query, extract data, pass it to `child`, then incorporate the result
    - `child` is a query run under `parent` context."
  [parent child]
  (reify DataQuery
    (-id [_] (-id parent))
    (-default-acceptor [_] (-default-acceptor parent))
    (-run [this data acceptor]
      (let [parent-data (run-query parent data (value-acceptor))]
        (-accept acceptor (-id this) (run-query child parent-data acceptor))))))

(comment
  (run-query (join-query (fn-query :a) (fn-query :b)) {:a {:b 2}}))

(defn vector-query
  "returns a query composed by `queries`, like `core/get-keys`
   - `queries` are subqueries run in same context."
  [queries]
  (reify DataQuery
    (-id [_] (map -id queries))
    (-default-acceptor [_] (value-acceptor))
    (-run [this data acceptor]
      (-accept
       acceptor
       (-id this)
       (transduce
        (map #(run-query % data))
        (fn
          ([acc] acc)
          ([acc item] (if item (merge acc item) (reduced nil))))
        {}
        queries)))))

(comment
  (run-query (vector-query [(fn-query :a) (fn-query :b)]) {:a 3 :b 4 :c 5}))

(defn seq-query
  "returns a query operate on sequence of maps
   - `q` is a query run on every item of the input sequence"
  [q]
  (reify DataQuery
    (-id [_] (-id q))
    (-default-acceptor [_] (value-acceptor))
    (-run [this coll acceptor]
      (-accept
       acceptor
       (-id this)
       (transduce 
        (map #(run-query q %))
        conj
        []
        coll)))))

(comment
  (run-query (seq-query (fn-query :a)) [{:a 1} {:a 2 :b 3} {}]))

(defn filter-query
  "returns a query filter the result with `q` only when it satisfies `pred`
   - `q` is a query
   - `pred` is a predict with data result from `q` as its argument"
  [q pred]
  (reify DataQuery
    (-id [_] (-id q))
    (-default-acceptor [_] (-default-acceptor q))
    (-run [this data ctx]
      (let [d (run-query q data (value-acceptor))]
        (-accept ctx (when (pred d) (-id this)) nil)))))

(comment
  (run-query (filter-query (fn-query :a) #(= % 2)) {:a 2})
  (run-query (vector-query [(filter-query (fn-query :a) odd?) (fn-query :b)]) {:a 1 :b 4 :c 5}))
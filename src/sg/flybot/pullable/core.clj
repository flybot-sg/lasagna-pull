; Copyright. 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.core
  "Implementation of queries.
   
   A query is a function which can extract k v from data."
  (:require
   [clojure.walk :refer [postwalk]]
   [sg.flybot.pullable.util :refer [data-error]]
   [sg.flybot.pullable.core.option :as option]))

(defprotocol Acceptor
  "An acceptor receives information"
  (-accept
    [acpt k v]
    "accept `k`,`v` pair, returns resulting data"))

(defprotocol DataQuery
  "Query"
  (-id
    [query]
    "returns the id (a.k.a key) of the query")
  (-default-acceptor
    [query]
    "return the default acceptor if the query is running independently")
  (-run
    [query data acceptor]
    "run `query` based on `data`, using `acceptor` to accept the result"))

(defn query?
  [x]
  (satisfies? DataQuery x))

(extend-protocol Acceptor
  nil
  (-accept [_ _ _] nil))

(defprotocol NamedQueryFactory
  "a factory to produce named query"
  (-named-query [factory q sym]
    "returns a named query based on query `q`, it will remember the result of `q`.
     - `sym`: a symbol for the result to output
     - `q`: the underlying query")
  (-symbol-values [factory]
    "returns a symbol -> value map."))

(defn run-query
  "runs a query `q` on `data` using `acceptor` (when nil uses q's default acceptor)"
  ([q data]
   (run-query q data nil))
  ([q data acceptor]
   (-run q data (or acceptor (-default-acceptor q)))))

(defn run-bind
  [q data]
  (let [fac (some-> q meta ::named-query-factory)]
    [(run-query q data) (when fac (-symbol-values fac))]))

;; Implementation

(defn- map-acceptor
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
   (fn-query k #(if (associative? %) (get % k) (data-error % k "expect associative"))))
  ([k f]
   (reify DataQuery
     (-id [_] k)
     (-default-acceptor [_] (map-acceptor {}))
     (-run [_ data acceptor]
       (-accept acceptor k (f data))))))

(comment
  (run-query (fn-query :a) {:b 3}))

(defn- value-acceptor
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
        (-accept
         acceptor
         (-id this)
         (when parent-data (run-query child parent-data (-default-acceptor child))))))))

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
      (if (sequential? coll)
        (-accept
         acceptor
         (-id this)
         (transduce
          (map #(run-query q %))
          conj
          []
          coll))
        (data-error coll (-id this) "expect sequential data")))))

(comment
  (run-query (seq-query (fn-query :a)) {:a 2})
  (run-query (seq-query (fn-query :a)) [{:a 1} {:a 2 :b 3} {}]))

(defn filter-query
  "returns a query filter the result with `q` only when it satisfies `pred`
   - `q` is a query
   - `pred` is a predict with arguments of `data` and value result from `q` as its argument"
  [q pred]
  (reify DataQuery
    (-id [_] (-id q))
    (-default-acceptor [_] (-default-acceptor q))
    (-run [this data ctx]
      (let [d (run-query q data (value-acceptor))]
        (-accept ctx (when (pred data d) (-id this)) nil)))))

(comment
  (run-query (filter-query (fn-query :a) #(= % 2)) {:a 2})
  (run-query (vector-query [(filter-query (fn-query :a) odd?) (fn-query :b)])
             {:a 1 :b 4 :c 5}))

(defn named-query-factory
  "returns a new NamedQueryFactory
   - `a-symbol-table`: an atom of map to accept symbol-> val pair"
  ([]
   (named-query-factory (atom {})))
  ([a-symbol-table]
   (reify NamedQueryFactory
     (-named-query [_ q sym]
       (reify DataQuery
         (-id [_] (-id q))
         (-default-acceptor [_] (-default-acceptor q))
         (-run [this data acceptor]
           (let [v        (-run q data (value-acceptor))
                 old-v    (get @a-symbol-table sym ::not-found)
                 set-val! #(do (swap! a-symbol-table assoc sym %) %)
                 rslt
                 (condp = old-v
                   ::not-found (set-val! v)
                   v           v
                   ::invalid   ::invalid
                   (set-val! ::invalid))] 
             (-accept acceptor (when (not= rslt ::invalid) (-id this)) rslt)))))
     (-symbol-values [_]
       (into
        {}
        (filter (fn [[_ v]] (not= ::invalid v)))
        @a-symbol-table)))))

(comment
  (def fac (named-query-factory))
  (run-query (vector-query [(-named-query fac (fn-query :a) 'a)
                            (-named-query fac (fn-query :b) 'b)]) {:a 2 :b 1})
  (-symbol-values fac))

;;### post-process-query
;; It is common to process data after it matches

(defn- vector-acceptor []
  (reify Acceptor
    (-accept [_ k v] [k v])))

(defn post-process-query
  "A query decorator post process its result. Given query `child`, the function
   `f-post-process` may change its value and return.
      - child: a query
      - f-post-process: [:=> [:kv-pair] :any]"
  ([child f-post-process]
   (reify DataQuery
     (-id [_] (-id child))
     (-default-acceptor [_] (-default-acceptor child))
     (-run [this data acceptor]
       (let [v (some-> (run-query child data (vector-acceptor))
                       (f-post-process)
                       (second))]
         (-accept acceptor (-id this) v))))))

;;## Post processors

;; Post processors apply after a query, abbreciate to `pp`

(defn decorate-query
  "Returns a query which is `q` decorated by options specified by `pp-pairs`."
  ([q pp-pairs]
   (reduce
    (fn [acc [pp-type pp-value]]
      (post-process-query
       acc
       (option/apply-post #:proc{:type pp-type :val pp-value})))
    q pp-pairs)))

(defn query-maker
  []
  (let [named-fac   (named-query-factory)
        f-map {:fn     fn-query
               :vec    vector-query
               :join   join-query
               :filter filter-query
               :seq    seq-query
               :named  (partial -named-query named-fac)
               :deco   decorate-query}]
    (fn [[query-type & args]]
      (with-meta
        (let [f (get f-map query-type)]
          (if f
            (apply f args)
            (throw (ex-info "unknown query type" {:type query-type}))))
        {::named-query-factory named-fac}))))

(comment
  (run-query ((query-maker) :fn :a) {:a 1 :b 2})
  )
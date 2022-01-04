(ns robertluo.pullable.query
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

(defn fn-query
  "returns a simple query has key as `k`, `f` to return a value from a map,
   and `child` query to execute if matches."
  ([k]
   (fn-query k #(get % k)))
  ([k f]
   (fn-query k f nil))
  ([k f child]
   (fn acc [data]
     #((or % map-acceptor)
       k
       (let [v (f data)]
         (when (not (nil? v))
           (let [child-q (or child (term-query v))]
             ((child-q v) nil))))))))

(comment
  (run-query (fn-query :a) {:a 3}))

(defn filter-query
  "returns a filter query which will not appears in result data, but will
   void other query if in a vector query, `pred` is the filter condition"
  [q pred]
  (fn [data]
    #((or % map-acceptor)
      (let [[k v] ((q data) vector)]
        (when (pred v) k))
      nil)))

(comment
  (run-query (filter-query (fn-query :a) #(= % 3)) {:a 2}))

;; A vector query is a query collection as `queries`
(defn vector-query
  "returns a vector query, takes other `queries` as its children,
   then apply them to data, return the merged map."
  [queries]
  (fn [data]
    (let [collector (transient [])
          v (some->> queries
                     (reduce (fn [acc q] ((q data)
                                          (comp
                                           #(if (nil? %) (reduced nil)  %)
                                           (partial accept conj! acc))))
                             collector)
                     (persistent!))]
      #((or % val-acceptor) (map first v) (into {} v)))))

(comment
  (run-query (vector-query [(fn-query :a) (fn-query :b)]) {:a 3 :b 5 :c 7}))

;; A SeqQuery can apply to a sequence of maps
(defn seq-query
  "returns a seq-query which applies query `q` to data (must be a collection) and return"
  ([q]
   (fn [data]
     (let [v (map #((q %) nil) data)
           k (if (seq v) (first v) ::should-never-be-seen)]
       #((or % val-acceptor) k v)))))

(comment
  (run-query (seq-query (vector-query [(fn-query :a) (fn-query :b)])) [{:a 3 :b 4} {:a 5} {}]))

(defn mk-named-var-query
  "returns a factory function which take a query `q` as its argument."
  ([t-sym-table sym]
   (mk-named-var-query t-sym-table (atom :fresh) sym))
  ([t-sym-table status sym]
   (fn [q]
     (fn [data]
       (let [[k v] ((q data) (fn [k v] [k v]))]
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
         #((or % map-acceptor) (when (not= @status :invalid) k) v))))))

(comment
  (let [a-sym-table (transient {})]
    [(run-query ((mk-named-var-query a-sym-table '?a) (fn-query :a)) {:a 3})
     (persistent! a-sym-table)]))

(defn named-var-factory
  []
  (let [t-sym-table (transient {})
        ;;cache for created named variable factory
        a-cx-named  (atom {})]
    [#(persistent! t-sym-table)
     (fn [sym]
       (or (get @a-cx-named sym)
           (mk-named-var-query t-sym-table sym)))]))

(defn run-bind
  [f-query data]
  (let [[f-sym-table f-named-var] (named-var-factory)]
    [(run-query (f-query f-named-var) data) (f-sym-table)]))

;;== pattern

(defn lvar?
  [x]
  (and (symbol? x) (re-matches #"\?.+" (name x))))

(defn kv->query
  [f k v]
  (f
   (cond
     (= v '?)    [:fn k]
     (lvar? v)   [:named (f [:fn k]) (symbol v)]
     (-> v meta ::query?) [:fn k k v]
     :else       [:filter (f [:fn k]) v])))

(require '[clojure.walk :refer [postwalk]])
(import '[clojure.lang IMapEntry])

(defn ->query
  [f x]
  (let [f (comp #(vary-meta % assoc ::query? true) f)]
    (postwalk
     (fn [x]
       (cond
         (map? x)                (->> (map #(apply kv->query f %) x) (vector :vec) f)
         (instance? IMapEntry x) x
         (vector? x)             (f [:seq (first x)])
         :else                   x))
     x)))

(comment
  (->query identity '{:a ? :b ?})
  (->query identity '{:a ?a})
  (->query identity '[{:a ?}])
  (->query identity '{:a {:b [{:c ?c}]}})
  )

(defn pattern->query
  [f-named-var x]
  (let [ctor-map {:fn fn-query
                  :vec vector-query
                  :seq seq-query
                  :filter (fn [q v] (filter-query q #(= % v)))
                  :named f-named-var}
        [x-name & args] x]
    (if-let [f (get ctor-map x-name)]
      (apply f args)
      (throw (ex-info (str "Do not understand: " x-name) {:op x-name :args args})))))

(comment
  (run-query (map->query (partial pattern->query identity) '{:a ?}) {:a 1})
  )
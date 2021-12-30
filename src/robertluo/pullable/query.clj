(ns robertluo.pullable.query)

(defn any-matcher
  "Returns a matcher that matches everything"
  []
  (fn [val]
    val))

(defn fn-matcher
  ([f]
   (fn-matcher f identity))
  ([f g]
   (fn [val]
     (some-> val f g))))

;;== Query implementations
(defrecord QueryResult [key val f])

(defn qr
  "returns a query result"
  ([]
   (qr nil nil))
  ([k v]
   (qr k v #(if v {k v} {})))
  ([k v f]
   (->QueryResult k v f)))

(def void?
  "predict if a query result is void, i.e. not matched."
  (comp nil? :key))

(defn run-query
  [q data]
  (when-let [{:keys [f]} (q data)]
    (f)))

(defn simple-query
  "returns a simple query has key as `k`, `f` to return a value from a map,
   and `child` query to execute if matches."
  ([k]
   (simple-query k #(get % k)))
  ([k f]
   (simple-query k f nil))
  ([k f child]
   (fn [data]
     (let [v (f data)]
       (if child
         (if-let [{:keys [f] :as rslt} (child v)]
           (if-not (void? rslt)
             (qr k (f))
             (qr))
           (qr))
         (qr k v))))))

(comment
  (run-query (simple-query :a) {:a 3})
  (run-query (simple-query :a) {:b 3})
  (run-query (simple-query :a :a (simple-query :b)) {:a {:b 3}})
  (run-query (simple-query :a :a (simple-query :b)) {:a 3}))

;; A vector query is a query collection as `queries`
(defn vector-query
  "returns a vector query, takes other `queries` as its children,
   then apply them to data, return the merged map."
  ([queries]
   (fn [data]
     (when-let [[k v] (->> (map #(% data) queries)
                           (reduce (fn [[ks rst] {:keys [key val]}]
                                     (if (nil? key)
                                       (reduced nil)
                                       [(conj ks key) (if val (conj rst [key val]) rst)]))
                                   [[] {}]))]
       (qr k v (fn [] v))))))

(comment
  (run-query (vector-query [(simple-query :a) (simple-query :b)]) {:a 3 :c 5}))

;; A SeqQuery can apply to a sequence of maps
(defn seq-query
  "returns a seq-query which applies query `q` to data (must be a collection) and return"
  ([q]
   (fn [data]     
     (let [qrs (map q data)
           v (map #((:f %)) qrs)
           k (when (seq qrs) (-> qrs first :key))]
       (qr k v (fn [] v))))))

(comment
  (run-query (seq-query (vector-query [(simple-query :a) (simple-query :b)])) [{:a 3 :b 4} {:a 5} {}]))

(defn scalar
  "Scalar query will not appear in query result"
  [q v]
  (fn [data]
    (when-let [{:keys [key val]} (q data)]
      (when (= val v)
        (qr key nil (constantly nil))))))

(defn named-var-factory
  []
  (let [sym-table (transient {})]
    [#(persistent! sym-table)
     (fn [sym]
       (let [status (atom :fresh)]
         (fn [q]
           (fn [data]
             (let [{:keys [key val f]} (q data)
                   new-f (fn []
                           (when (= @status :found)
                             (f)))]
               (case @status
                 :fresh
                 (when qr
                   (assoc! sym-table sym val)
                   (reset! status :found)
                   (qr key val new-f))

                 :found
                 (if (not= (get sym-table sym) val)
                   (do
                     (dissoc! sym-table sym)
                     (reset! status :invalid)
                     (qr))
                   (qr key val new-f))

                 :invalid
                 (qr)))))))]))

(comment
  (run-query (vector-query [(simple-query :a) (scalar (simple-query :b) 2)]) {:a 5 :b 2})
  (let [[symbols named-var] (named-var-factory)
        a-var (named-var 'a)]
    [(run-query (a-var (simple-query :a)) {:a 5 :b 1}) (symbols)])

  (let [[symbols named-var] (named-var-factory)
        a-var (named-var 'a)]
    [(run-query (vector-query [(a-var (simple-query :a)) (a-var (simple-query :b))]) {:a 5 :b 5}) (symbols)])

  (let [[symbols named-var] (named-var-factory)
        a-var (named-var 'a)]
    [(run-query (vector-query
                 [(a-var (simple-query :a))
                  (simple-query :c :c (a-var (simple-query :b)))]) {:a 5 :c {:b 5}}) (symbols)]))
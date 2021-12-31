(ns robertluo.pullable.query)

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
     (or
      (when-let [[k v] (->> (map #(% data) queries)
                            (reduce (fn [[ks rst] {:keys [key val]}]
                                      (if (nil? key)
                                        (reduced nil)
                                        [(conj ks key) (if val (conj rst [key val]) rst)]))
                                    [[] {}]))]
        (qr k v (fn [] v)))
      (qr)))))

(comment
  (run-query (vector-query [(simple-query :a) (simple-query :b)]) {:a 3 :c 5}))

;; A SeqQuery can apply to a sequence of maps
(defn seq-query
  "returns a seq-query which applies query `q` to data (must be a collection) and return"
  ([q]
   (fn [data]     
     (let [qrs (map q data)
           v (map (fn [{:keys [f]}] (f)) qrs)
           k (when (seq qrs) (-> qrs first :key))]
       (qr k v (fn [] v))))))

(comment
  (run-query (seq-query (vector-query [(simple-query :a) (simple-query :b)])) [{:a 3 :b 4} {:a 5} {}]))

(defn scalar
  "Scalar query will not appear in query result"
  [q v]
  (fn [data]
    (or
     (when-let [{:keys [key val]} (q data)]
       (when (= val v)
         (qr key nil (constantly nil))))
     (qr))))

(defn named-var-factory
  []
  (let [sym-table (transient {})
        cx-named (atom {})]
    [#(persistent! sym-table)
     (fn [sym]
       (or (get @cx-named sym)
           (let [status (atom :fresh)
                 f (fn [q]
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
                           (qr)))))]
                      (swap! cx-named assoc sym f)
                      f)))]))

;;== pattern

(defn lvar?
  [x]
  (and (symbol? x) (re-matches #"\?.+" (name x))))

(defn ->query
  ([f-name-var x]
   (cond
     (map? x)
     (->> (for [[k v] x]
            (cond
              (= v '?)
              (simple-query k)

              (lvar? v)
              ((f-name-var v) (simple-query k))

              (map? v)
              (simple-query k k (->query f-name-var v))

              :else
              (scalar (simple-query k) v)))
          (vector-query))
     
     (vector? x)
     (let [[v var] x
           g (if (and var (lvar? var)) (f-name-var var) identity)]
       (-> (->query f-name-var v)
           (seq-query)
           (g)))

     (keyword? x)
     (simple-query x)

     :else
     (throw (ex-info (str "Not known pattern: " x) {:x x})))))

(defn run-bind
  [pattern data]
  (let [[symbols named-var] (named-var-factory)]
    [(run-query (->query named-var pattern) data) (symbols)]))


(comment
  (run-bind '{:a ? :b 2 :c {:d ?d}} {:a 1 :b 2 :c {:d 4}})
  (run-bind '[{:a ?} ?x] [{:a 1} {:b 1}])
  (run-bind '[{:b ?b}] [{:a 1, :b 2, :c [{:d 3, :e 4}]} {:a 5, :b 6} {:c [{:e 7, :f 8}]}])
  )
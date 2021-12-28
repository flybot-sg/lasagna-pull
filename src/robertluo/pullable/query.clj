(ns robertluo.pullable.query
  "The core construct of queries.
   A query is a continuation function which take abitary data as its argument,
   returns a derived data structure.
   
   Continuation means it takes the next step as an argument, each query
   will call its next step. This is a natural way to express join."
  (:require [clojure.string :as str]))

(def IGNORE
  "Ignore value"
  :pull/ignore)

(defprotocol Query
  (-key [query] "returns the key of the `query`")
  (-value [query data] "returns the value on `data`")
  (-matching [query data] "returns the matching result of `data`"))

(defprotocol ValueMatcher
  (-matches? 
   [matcher val]
   "predict if `matcher` matches `val`"))

(defn continue? [v]
  (not= IGNORE v))

(defrecord AnyMatcher []
  ValueMatcher
  (-matches? [_ val]
    (when (continue? val)
      val)))

(def any-matcher ->AnyMatcher)

(defrecord NamedMatcher [f g]
  ValueMatcher
  (-matches?
   [_ val]
   (let [v (f val)]
     #dbg
     (when (continue? (f val))
       (g v)))))

(defn named-matcher
  ([f]
   (named-matcher f identity))
  ([f g]
   (->NamedMatcher f g)))

(comment
  (-matches? (any-matcher) IGNORE)
  (-matches? (any-matcher) 3)
  )

(defn matches [query data]
  (let [v (-value query data)]
    (cond-> {}
      (continue? v) (assoc (-key query) v))))

(defrecord SimpleQuery [k f child matcher]
  Query
  (-key [_] k)
  (-value
    [_ data]
    (let [val (f data)]
      (if-not (-matches? matcher val)
        IGNORE
        (if child
          (-matching child val)
          val))))
  (-matching
   [this data]
   (matches this data)))

(defn simple-query
  ([k]
   (simple-query k #(get % k IGNORE)))
  ([k f]
   (simple-query k f nil))
  ([k f child]
   (simple-query k f child (any-matcher)))
  ([k f child matcher]
   (->SimpleQuery k f child matcher)))

(defrecord VectorQuery [queries matcher]
  Query
  (-key [_] (map -key queries))
  (-value
   [_ data]
   (map #(-value % data) queries))
  (-matching
   [this data]
   (->> data
        (-value this)
        (filter #(-matches? matcher %))
        (map vector (map -key queries))
        (into {}))))

(defn vector-query
  ([queries]
   (vector-query queries (any-matcher)))
  ([queries matcher]
   (->VectorQuery queries matcher)))

(defrecord SeqQuery [q matcher]
  Query
  (-key [_] (-key q))
  (-value
   [_ data]
   (map #(-value q %) data))
  (-matching
   [_ data]
   (let [v (map #(-matching q %) data)]
     #dbg
     (when (-matches? matcher v)
       v))))

(defn seq-query
  ([q]
   (seq-query q (any-matcher)))
  ([q matcher]
   (->SeqQuery q matcher)))

(comment
  (-matching (simple-query :a) {:a 3 :b 4})
  (-matching (simple-query :a :a (simple-query :b)) {:a {:b 3 :c 4}})
  (-matching (vector-query [(simple-query :a) (simple-query :b)]) {:a 3 :c 5})
  (-matching (seq-query (vector-query [(simple-query :a) (simple-query :b )])) [{:a 3 :b 4} {:a 5} {}])
  )

(defn error
  [msg data]
  (throw (ex-info msg data)))

(defn create-matcher
  ([x]
   (create-matcher x (fn [_ v] v)))
  ([x f-binding]
   (cond
     (= x '?)
     [false (any-matcher)]

     (map? x)
     [true (any-matcher)]

     (str/starts-with? (str x) "?")
     (let [variable-name (subs (str x) 1)
           f (partial f-binding (symbol variable-name))]
       [false (named-matcher identity f)])

     :else
     [false (named-matcher identity)])))

(comment
  (create-matcher '?)
  (create-matcher '?a (atom nil))
  )

(defn ->query
  ([x]
   (->query x (atom nil)))
  ([x f-binding]
   (cond
     (map? x)
     (vector-query
      (for [[k v] x]
        (let [[child? matcher] (create-matcher v f-binding)
              child (when child? (->query v f-binding))]
          (simple-query k k child matcher))))

     (vector? x)
     (let [[qx matcher-x] x
           [_ matcher] (create-matcher matcher-x f-binding)
           matcher (or matcher (any-matcher))]
       (seq-query (->query qx f-binding) matcher))

     :else
     (error "Not able to construct query" {:x x}))))

(defn matching
  [x data]
  (let [atom-bindings (atom nil)
        f-bindings (fn [variable-name v]
                     (swap! atom-bindings assoc variable-name v)
                     v)]
    [(-matching (->query x f-bindings) data) @atom-bindings]))

(def run (comp first matching))

(comment
  (->query '{:a ? :b ?})
  (->query '{:a {:b ?}})
  (->query '[{:a ?} ?a])
  (run '{:a 3 :b ?} {:a 4 :b 4})
  (matching '{:a ?a :b {:c ?c}} {:a 3 :b {:c 5} :d 7})
  (run '{:a ? :b ?} {:a 3 :b 4 :c 5})
  (run '{:a {:b ?}} {:a {:b 3 :c 5}})
  (matching '[{:a ?} ?a] [{:a 3 :b 4} {}])
  (run '[{:a [{:b ?}]}] [{:a [{:b 3} {:b 4 :c 5}]}])
  )
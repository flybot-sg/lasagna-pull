(ns robertluo.pullable
  "Pull from data structure by using pattern."
  (:require
   [robertluo.pullable 
    [core :as core]
    [pattern :as ptn]]))

(defn- pattern->query
  "take a function `f-named-var` to create named variable query, and `x`
   is the expression to specify whole query,
   returns a function takes named variable constructor"
  [f-named-var]
  (fn [x]
    (let [ctor-map {:fn     core/fn-query
                    :vec    core/vector-query
                    :seq    core/seq-query
                    :filter (fn [q v] (core/filter-query q (if (fn? v) v #(= % v))))
                    :named  (fn [q sym] ((f-named-var sym) q))
                    :join   core/join-query
                    :deco   (fn [q pp-pairs]
                              (core/decorate-query q pp-pairs))}
          [x-name & args] x]
      (if-let [f (get ctor-map x-name)]
        (apply f args)
        (ptn/pattern-error! "not understandable pattern" x)))))

(defn query
  "Returns a query from `pattern`. A query can be used to
   pull from data structure later. "
  [pattern]
  (with-meta
    (fn [f-named-var]
      (ptn/->query (pattern->query f-named-var) pattern))
    {::compiled? true}))

(defn run
  "Given `data`, run a query returned by `query` and returns the pull result."
  [pattern data]
  (core/run-bind (if (some-> pattern meta ::compiled?) pattern (query pattern)) data))

(comment
  (core/id (query '{:a ?}))
  (run '{(:a :with [{}]) {(:b :not-found ::ok) ?}} {:a identity})
  )
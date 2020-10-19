(ns robertluo.pullable.pattern
  (:require
   [robertluo.pullable.query :as core])
  (:import
   [clojure.lang IPersistentVector IPersistentMap IPersistentList]))

(defprotocol QueryStatement
  (-as-query [statement]
    "create a query from statement"))

(defn- make-options
  [query opt-pairs]
  (reduce (fn [q [ot ov]]
            (core/create-option {:option/query q :option/type ot :option/arg ov}))
          query opt-pairs))

(defn- option-map [x]
  (let [[q & options] x]
    [q (->> options (partition 2) (mapv vec) (into {}))]))

(extend-protocol QueryStatement
  Object
  (-as-query [this]
    (core/->SimpleQuery this))
  IPersistentVector
  (-as-query [this]
    (core/->VectorQuery (map -as-query this)))
  IPersistentMap
  (-as-query [this]
    (let [[k v] (first this)]
      (core/->JoinQuery (-as-query k) (-as-query v))))
  IPersistentList
  (-as-query [this]
    (let [[q opt-pairs] (option-map this)
          query         (-as-query q)]
      (make-options query opt-pairs))))


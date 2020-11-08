(ns robertluo.pullable.pattern
  (:require
   [robertluo.pullable.core :as core])
  (:import
   [clojure.lang IPersistentVector IPersistentMap IPersistentList]))

(defprotocol QueryStatement
  (-as-query [statement context]
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
  (-as-query [this context]
    (core/simple-query context this))
  IPersistentVector
  (-as-query [this context]
    (let [child-context (assoc context ::core/type :vector)]
      (core/vector-query context (map #(-as-query % child-context) this))))
  IPersistentMap
  (-as-query [this context]
    (let [[k v] (first this)
          child-context (assoc context ::core/type :join)]
      (core/join-query context (-as-query k child-context) (-as-query v child-context))))
  IPersistentList
  (-as-query [this context]
    (let [[q opt-pairs] (option-map this)
          query         (-as-query q context)]
      (make-options query opt-pairs))))

(defn as-query
  "returns a query"
  ([pattern]
   (as-query pattern nil))
  ([pattern context]
   (-as-query pattern context)))


(ns robertluo.pullable.query)

(defprotocol Transformer
  (-transform [_ m value]))

(defrecord SimpleKey [k]
  Transformer
  (-transform [_ m value]
    (conj m [k value])))

(defn simple-query [k]
  (fn [m]
    [(SimpleKey. k) (get m k)]))

(defn join-query
  [key-query val-query]
  (fn [m]
    (let [[k v] (key-query m)]
      (-transform k (empty m)
                  (let [[v-k v-v] (val-query v)]
                    (-transform v-k (empty v) v-v))))))

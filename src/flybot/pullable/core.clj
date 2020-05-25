(ns flybot.pullable.core)

(defprotocol Query
  (-select
   [q data]
   "select from data"))

(defrecord CoreQuery [key children not-found seq?]
  Query
  (-select
    [_ data]
    (let [sel-merge (fn [elem]
                      (->> (map #(-select % elem) children)
                           (apply merge)))
          sub-data  (if key (get data key not-found) data)
          v         (if (and (not= sub-data not-found) (seq children))
                      (if seq?
                        (map sel-merge sub-data)
                        (sel-merge sub-data))
                      sub-data)]
      (if key {key v} v))))

(defn query
  [qspec]
  (map->CoreQuery qspec))

;;====================
;; Pattern

(defprotocol Queryable
  (-to-query
   [queryable]
   "returns a Query of itself"))

(extend-protocol Queryable
  nil
  (-to-query
    [_]
    (query {}))
  Object
  (-to-query
    [this]
    (query {:key this}))
  clojure.lang.PersistentVector
  (-to-query
   [this]
   (query {:children (map -to-query (.seq this))}))
  clojure.lang.IPersistentMap
  (-to-query
    [this]
    (if-let [[k v] (first this)]
      (-> (assoc (-to-query v) :key k)
          (merge (dissoc this k)))
      (-to-query nil))))

(defn pattern->query
  [ptn]
  (-to-query ptn))
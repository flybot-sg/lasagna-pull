(ns flybot.pullable.core)

(defprotocol Query
  (-select
   [q data]
   "select from data"))

(defrecord CoreQuery [key children]
  Query
  (-select
   [_ data]
   (let [sub-data (if key (get data key) data)
         v (if-let [children (seq children)]
             (->> (map #(-select % sub-data) children)
                  (apply merge))
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
      (assoc (-to-query v) :key k)
      (-to-query nil))))

(defn pattern->query
  [ptn]
  (-to-query ptn))
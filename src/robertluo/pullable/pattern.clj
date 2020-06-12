(ns robertluo.pullable.pattern
  "defines a pattern expression language to write query easily")

;;====================
;; Pattern

(defprotocol Queryable
  (-to-query
   [queryable]
   "returns a Query of itself"))

(defn normalize [v]
  (if (vector? v) v [v]))

(defn expand-depth
  [depth m]
  (assert (pos? depth) "Depth must be a positive number")
  (let [[k v] (first m)
        d     (let [v (dec depth)] (when (pos? v) v))
        v     (conj (normalize v) (cond-> (assoc m :not-found ::ignore)
                                    d (assoc :depth d)))]
    (assoc m k v)))

(extend-protocol Queryable
  nil
  (-to-query
    [_]
    {})

  Object
  (-to-query
    [this]
    {:key this})

  ;;vector defines an empty query contains children
  clojure.lang.PersistentVector
  (-to-query
   [this]
    {:children (map -to-query (.seq this))})

  ;;Clojure list? definition is IPersistentList, but Transit library use Java list, what a mess!
  java.util.List
  (-to-query
    [[v & options]]
    (assoc (-to-query v) :options (partition 2 options)))

  ;;Map to specific key, children and options at same time
  clojure.lang.IPersistentMap
  (-to-query
   [this]
   (if-let [depth (:depth this)]
     ;;:depth is a special option, using rewrite to implement it
     (-to-query (expand-depth depth (dissoc this :depth)))
     (if-let [[k v] (first this)]
       (let [options (dissoc this k)]
         (assoc (-to-query (normalize v)) :key k :options options))
       (-to-query nil)))))

(defn pattern->query
  [ptn]
  (-to-query ptn))

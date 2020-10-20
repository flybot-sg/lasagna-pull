(ns robertluo.pullable.reader
  "Reader functions to rewrite patterns")

(defn normalize [v]
  (if (vector? v) v [v]))

(defn add-element
  [m c]
  (let [[k v] (first m)]
    {k (conj (normalize v) c)}))

(defn expand-depth
  [[m depth]]
  (assert (pos? depth) "Depth must be a positive number")
  (loop [m m
         d depth]
    (if (zero? d)
      m
      (recur (add-element m m) (dec d)))))

(comment
  (expand-depth [{:a :b} 1]))

(defn add-option
  [m options]
  (let [[k v] (first m)]
    {(concat (if (list? k) k (list k)) options) v}))

(comment
  (add-option {:a 3} [:as :b]))



(ns robertluo.pullable.pattern
  "Pull pattern definition")

;;== pattern

(defn pattern-error
  [reason pattern]
  (throw (ex-info reason {:pattern pattern})))

(defn lvar?
  "predict if `x` is a logical variable, i.e, starts with `?` and has a name,
     - ?a is a logic variable
     - ? is not"
  [x]
  (and (symbol? x) (re-matches #"\?.+" (name x))))

(defn ->query
  [f x]
  (letfn [(apply-opts
           [qr opts]
           (if (seq opts)
             (f [:deco qr (partition 2 opts)])
             qr))
          (val-of
           [k v]
           (let [[q & opts] (if (sequential? v) v (list v))]
             (cond
               (= '? q)
               (apply-opts (f [:fn k]) opts)

               (lvar? q)
               (f [:named (apply-opts (f [:fn k]) opts) q])

               (or (map? v) (vector? v))
               (f [:fn k k (->query f v)])

               :else
               (f [:filter (apply-opts (f [:fn k]) opts) q]))
             ))]
    (cond
      (map? x)
      (f [:vec (map #(apply val-of %) x)])
      
      (vector? x)
      (let [[q var-name & opts] x
            qr (apply-opts (f [:seq (->query f q)]) opts)]
        (cond
          (lvar? var-name)
          (f [:named qr var-name])

          (or (nil? var-name) (= '? var-name))
          qr
          
          :else
          (pattern-error "seq options must start with a variable" x)))
      
      :else
      (pattern-error "unable to understand" x))))

(comment
  (->query #(concat % ['ok]) '{:a (?a :with [3])})
  (->query #(concat % ['ok]) '{:a ?a})
  (->query identity '[{:a ?} ?x])
  (->query identity '{a {:b ?b}}))

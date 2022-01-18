(ns robertluo.pullable.pattern
  "Pull pattern definition")

;;== pattern

(defn pattern-error!
  [reason pattern]
  (throw (ex-info reason {:pattern pattern})))

(defn lvar?
  "predict if `x` is a logical variable, i.e, starts with `?` and has a name,
     - ?a is a logic variable
     - ? is not"
  [v]
  (and (symbol? v) (re-matches #"\?.+" (name v))))

;;FIXME use trampoline to protect stack
(defn ->query
  "compile `pattern` by applying query creation function `f` to it."
  [f pattern]
  (letfn [(apply-opts
           [qr opts]
           (if (seq opts)
             (f [:deco qr (partition 2 opts)])
             qr))
          (val-of
           [k v]
           (let [[k & opts] (if (sequential? k) k (list k))
                 q (apply-opts (f [:fn k]) opts)]
             (cond
               (= '? v)
               q

               (lvar? v)
               (f [:named q v])

               (or (map? v) (vector? v))
               (f [:join q (->query f v)])

               :else
               (f [:filter q v]))
             ))]
    (cond
      (map? pattern)
      (f [:vec (map #(apply val-of %) pattern)])
      
      (vector? pattern)
      (let [[q var-name & opts] pattern
            qr (apply-opts (f [:seq (->query f q)]) opts)]
        (cond
          (lvar? var-name)
          (f [:named qr var-name])

          (or (nil? var-name) (= '? var-name))
          qr
          
          :else
          (pattern-error! "seq options must start with a variable" pattern)))
      
      :else
      (pattern-error! "unable to understand" pattern))))

(comment
  (->query #(concat % ['ok]) '{(:a :with [{:b 2 :c 3}]) {:b ?}})
  )

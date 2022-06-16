; Copyright. 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns robertluo.pullable.pattern
  "Pull pattern definition.")

(defn pattern-error!
  "Throws an error indicates pattern error."
  [reason pattern]
  (throw (ex-info reason {:pattern pattern})))

(defn lvar?
  "Predicts if `x` is a logical variable, i.e, starts with `?` and has a name,
     - ?a is a logic variable
     - ? is not"
  [v]
  (and (symbol? v) (re-matches #"\?.+" (name v))))

;; Query construct function
;; This is independent to query design by using function `f`.
;;FIXME use trampoline to protect stack
(defn ->query
  "Compiles `pattern` by applying query creation function `f` to it."
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

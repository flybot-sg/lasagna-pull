; Copyright. 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.pattern
  "Pull pattern definition."
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]))

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

(defn filter-maker
  [x]
  (if (fn? x) (fn [_ v] (x v)) (fn [_ exp] (= exp x))))

;; Query construct function
;; This is independent to query design by using function `f`.
;;FIXME use trampoline to protect stack
(defn ->query
  "Compiles `pattern` by applying query creation function `f` to it."
  ([f pattern]
   (->query f pattern identity))
  ([f pattern filter-maker]
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
                 (f [:join q (->query f v filter-maker)])

                 :else
                 (f [:filter q (filter-maker v)]))))]
     (cond
       (map? pattern)
       (f [:vec (map #(apply val-of %) pattern)])

       (vector? pattern)
       (let [[q var-name & opts] pattern
             qr (apply-opts (f [:seq (->query f q filter-maker)]) opts)]
         (cond
           (lvar? var-name)
           (f [:named qr var-name])

           (or (nil? var-name) (= '? var-name))
           qr

           :else
           (pattern-error! "seq options must start with a variable" pattern)))

       :else
       (pattern-error! "unable to understand" pattern)))))

(comment
  (->query #(concat % ['ok]) '{(:a :with [{:b 2 :c 3}]) {:b ?}}) 
  )

(defn valid-symbol?
  "Returns true if `p` is a symbol starting with ?"
  [p]
  (and (symbol? p) (re-matches #"\?.*" (name p))))


(def sch-pattern-proc
  "Malli schema for the different post-processors in the keys.
   Expects individual post-processors with format [proc arg]
   i.e. [:not-found 0]"
  [:multi {:dispatch first}
   [:when [:tuple [:= :when] fn?]]
   [:not-found [:tuple [:= :not-found] :any]]
   [:with [:tuple [:= :with] vector?]]
   [:batch [:tuple [:= :batch] [:vector vector?]]]
   [:watch [:tuple [:= :watch] fn?]]])

(defn valid-proc-keys?
  "Validates the key with post-processors such as (:a :not-found 0 :when even?)."
  [k] 
  (->> k
       rest
       (partition 2) 
       (map #(m/validate sch-pattern-proc (into [] %))) 
       (every? true?)))

(def sch-pattern
  "Malli schema for the pattern."
  [:schema
   {:registry
    {::pattern
     [:or
      [:map-of
       [:or
        ;; simple keyword
        :keyword
        ;; key with post-processors
        [:fn valid-proc-keys?]]
       [:or
        ;; '? or '?x
        [:fn valid-symbol?]
        ;; filters
        [:fn #(not (sequential? %))]
        ;; single pattern
        [:ref ::pattern]
        ;; sequence of maps
        [:tuple [:ref ::pattern]]
        ;; sequence of maps with logical variable
        [:tuple [:ref ::pattern] [:maybe [:fn valid-symbol?]]]
        ;; seq post-processor
        [:tuple [:ref ::pattern] [:fn valid-symbol?] [:= :seq] [:sequential any?]]]]]}}
   [:or
    ::pattern
    [:tuple ::pattern]
    [:tuple ::pattern [:maybe [:fn valid-symbol?]]]
    [:tuple ::pattern [:fn valid-symbol?] [:= :seq] [:sequential any?]]]])

(defn validate-pattern
  "Runs the `pattern` against the malli schema.
   Returns the `pattern` if pattern is valid, else throws error."
  [pattern] 
  (let [validator (m/validator sch-pattern)]
    (if (validator pattern)
      pattern
      (throw
       (let [err (mu/explain-data sch-pattern pattern)]
         (ex-info (str (me/humanize err))
                  {:pattern pattern :error err}))))))

(comment 
  (m/validate sch-pattern
   [{(list :a :not-found 3 :when odd?) '?
    '(:b :with [3]) {(list :c :when odd?) '?c :d even?}}]))
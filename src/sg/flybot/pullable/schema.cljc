; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns ^:no-doc sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
  (:require [malli.core :as m]
            [malli.util :as mu]))
 
(defn lvar?
  "predict if `x` is a logical var, a.k.a lvar"
  [x]
  (boolean (and (symbol? x) (re-matches #"\?.*" (str x)))))

^:rct/test
(comment
  (lvar? '?) ;=> true
  (lvar? '?x) ;=> true
  (lvar? 'other) ;=> false
  )

;;-------------------
;; malli's map schema can not check key's options since it just lookup a simple
;; key, however, we need to put a key in a list, so we need wrap a map schema
;; inside a custom schema.

;; TODO
;; Malli does not contains function to determine if a type is sequential like, 
;; or an equivlent as a function. But we need to know it in order to make different
;; options for them. The following two functions may need better implementation.
;; for example, `[:not list?]` should actually be treated as `:any`.

(let [types #{:vector :sequential :set (m/type vector?) (m/type set?)}]
  (defn- seq-type?
    "predict `x` type is a sequential type"
    [t]
    (boolean (types t))))

;;TODO options are fixed, should make it multimethod allowing expanding
(defn- options-of
  "returns pullable pattern option for `schema`"
  [schema]
  (let [args-trans (fn [s]
                     (case (m/type s)
                       :cat (if-let [children (seq (m/children s))]
                              (m/into-schema :tuple (m/properties s) children)
                              empty?)
                       :catn (m/into-schema :tuple (m/properties s) (map #(nth % 2) (m/children s)))
                       :else s))
        =>arg (fn [acc s]
                (let [[args-schema] (m/children s)
                      args-schema (args-trans args-schema)]
                  (into acc [[:cat [:= :with] args-schema]
                             [:cat [:= :batch] [:vector args-schema]]])))]
    [:cat
     [:*
      (into
       [:alt]
       (let [t (m/type schema)]
         (cond-> [[:cat [:= :default] schema]
                  [:cat [:= :not-found] schema]
                  [:cat [:= :when] fn?]]
           (= :=> t)
           (=>arg schema)
           
           (= :any t)
           (into [[:cat [:= :with] vector?]
                  [:cat [:= :batch] [:vector vector?]]])
           
           (or (= :any t) (seq-type? t))
           (into [[:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]))))]]))

^:rct/test
(comment
  (def try-val #(-> % (options-of) (m/explain %2)))
  (try-val :int [:default 5]) ;=> nil
  (try-val :int [:default :ok]) ;=>> (complement nil?)  
  (try-val [:=> [:cat :int] :string] [:with [3]]) ;=> nil
  (try-val [:=> [:cat] :string] [:with []]) ;=> nil
  )

(defn- mark-ptn
  "mark a malli `schema` is a pullable pattern"
  [schema]
  (mu/update-properties schema assoc ::pattern? true))

(defn- ptn?
  "predict if a malli schema is a pullable pattern"
  [schema]
  (or (= :map-pattern (m/type schema))
      (-> (m/properties schema) ::pattern?)))

(defn- val-of
  [schema]
  (if (ptn? schema)
    schema
    (m/schema [:or schema [:fn lvar?]])))

(defn- explain-options
  "returns a pair of result schema, and explaining result for options"
  [schema path o in acc]
  (if (seq o)
    [(cond-> schema (= :=> (m/type schema)) (-> m/children second))
     ((m/-explainer (m/schema (options-of schema)) path) o in acc)]
    [schema acc]))

(defn- pattern-explainer
  "explain `schema` on `path`, if `continue?` is false, stops on first error"
  [schema path continue?]
  (let [keyset (m/-entry-keyset (m/-entry-parser schema))
        m-error (fn [path in schema value type] {:path path, :in in, :schema schema, :value value, :type type}) 
        normalizer (fn [m] (into {} (for [[k v] m] (if (list? k) [(first k) [v (rest k)]] [k [v]]))))
        explainers
        (-> (m/-vmap
             (fn [[key _ schema]]
               (fn [x in acc]
                 (if-let [[_ [v o]] (find x key)]
                   (let [[sc acc] (explain-options schema path o (conj in key) acc)]
                     ((m/-explainer (val-of sc) path) v (conj in key) acc))
                   acc)))
             (m/-children schema))
            (conj (fn [x in acc]
                    (reduce-kv
                     (fn [acc k v]
                       (if (contains? keyset k)
                         acc
                         (conj acc (m-error (conj path k) (conj in k) schema v ::m/extra-key))))
                     acc x))))]
    (fn [x in acc]
      (if-not (map? x)
        (conj acc (m-error path in schema x ::m/invalid-type))
        (let [x' (normalizer x)]
          (reduce
           (fn [acc explainer]
             (let [acc (explainer x' in acc)]
               (cond-> acc (and (seq acc) (not continue?)) (reduced))))
           acc explainers))))))

(defn- pattern-map-schema
  ([map-schema]
   ^{:type ::into-schema}
   (reify
     m/AST
     (-from-ast [parent ast options] (m/-from-entry-ast parent ast options))
     m/IntoSchema
     (-type [_] :map-pattern)
     (-type-properties [_])
     (-properties-schema [_ _])
     (-children-schema [_ _])
     (-into-schema [parent _ _ _]
       (let [entry-parser (m/-entry-parser map-schema)]
         ^{:type ::schema}
         (reify
           m/AST
           (-to-ast [this _] (m/-entry-ast this (m/-entry-keyset entry-parser)))
           m/Schema
           (-explainer [this path]
             (pattern-explainer this path true)) 
           (-validator [this] (fn [x] (-> x ((pattern-explainer this [] false) [] []) seq boolean not)))
           (-walk [this walker path options] (m/-walk-entries this walker path options))
           (-properties [_] (m/-properties map-schema))
           (-options [_] (m/-options map-schema))
           (-children [_] (m/-entry-children entry-parser))
           (-parent [_] parent)
           (-form [_] [:pattern-map (m/-form map-schema)])
           m/EntrySchema
           (-entries [_] (m/-entry-entries entry-parser))
           (-entry-parser [_] entry-parser)
           m/LensSchema
           (-keep [_] true)
           (-get [this key default] (m/-get-entries this key default))
           (-set [this key value] (m/-set-entries this key value))))))))

;;-------------------------------
; Public API

(defn normalize-schema 
  "IMO malli's schema is too liberal, let's normalize it for some
  alternative (a.k.a should not be allowed in the first place)"
  [schema]
  (let [t (m/type schema)]
    (-> #_{:clj-kondo/ignore [:quoted-case-test-constant]}
        (case t
          'fn? [:=> [:cat :any] :any] 
          schema)
        (m/schema))))

^:rct/test
(comment
  (normalize-schema :int) ;=>> #(= :int (m/type %))
  (normalize-schema fn?) ;=>> #(= :=> (m/type %)) 
  )

(defn pattern-schema-of 
  "returns a pattern schema for given `data-schema`, default to general map or
   sequence of general maps.

  You can use this returned schema to check your pattern, using malli's `validate`/`validator`,
  and `explain`/`explainer` functions.

  The validator/explainer produced will try catch all problems which can be inferred from
  the `data-schema`. For example, if a data schema specified a map only contains `:a` and `:b`
  keys, patterns which asking for any other keys will fail. This makes a good strategy if you want
  limit the visibility of your data to the users. Extremely useful for remote pulling."
  ([]
   (pattern-schema-of nil))
  ([data-schema]
   (m/walk
    (or data-schema [:or
                     [:map-of :any :any]
                     [:sequential [:map-of :any :any]]])
    (m/schema-walker
     (fn [sch]
       (let [sch (normalize-schema sch)
             t (m/type sch)]
         (cond
           (= :map t)
           (-> sch (pattern-map-schema) (mark-ptn))

           (= :map-of t)
           (-> sch
               (mu/transform-entries
                (fn [[key-type val-type]]
                  (let [vector-ptn [:or val-type [:fn lvar?] (options-of val-type)]]
                    [key-type vector-ptn])))
               (mark-ptn))

           (and (seq-type? t) (seq (m/children sch)))
           (let [x (-> sch m/children first)]
             (if (ptn? x)
               (-> [:cat 
                    x 
                    [:? [:fn lvar?]] 
                    [:? [:alt [:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]]]
                   (m/schema)
                   (mark-ptn))
               sch))

           :else sch)))))))

^:rct/test
(comment
  ;;simple pattern
  (def ptn-schema (pattern-schema-of (m/schema [:map [:a :int]])))
  (m/explain ptn-schema '{:a ?}) ;=> nil
  (m/explain ptn-schema '{(:a) ?}) ;=> nil
  (m/explain ptn-schema '{(:a :default 0) ?});=> nil  (m/explain ptn-schema '{(:a default :ok) ?}) ;=>> (complement nil?)
  
  ;;nesting pattern
  (def ptn-schema2 (pattern-schema-of [:map [:a [:map [:b :int]]]]))
  (m/explain ptn-schema2 '{:a {:b :ok}}) ;=>> (complement nil?)
  (m/explain ptn-schema2 '{:a {(:b :default 0) ?}}) ;=> nil
  ;;the validation is simple, just a short-circuit of explainer
  (m/validate ptn-schema2 '{:a {(:b :default :ok) ?}}) ;=> false
  (m/validate ptn-schema2 '{:b ?});=> false
  ;;disallow directly fetch nesting
  (m/explain ptn-schema2 '{:a ?}) ;=>> (complement nil?)
  
  ;;sequential pattern
  (def ptn-schema3 (pattern-schema-of [:sequential [:map [:a :string]]]))
  (m/explain ptn-schema3 '[{:a ?} ?x :seq [1 2]]) ;=> nil
  
  ;;with pattern can pick up function schema
  (def ptn-schema4 (pattern-schema-of [:map
                                       [:a [:=> [:cat :int :keyword] :int]]
                                       [:b [:=> [:catn [:c :string]] :int]]
                                       [:c [:=> [:cat] :int]]]))
  (m/explain ptn-schema4 '{(:a :with [3 :foo]) ?}) ;=> nil
  (m/explain ptn-schema4 '{(:a :with [:ok :ok]) ?}) ;=>> (complement nil?)
  (m/explain ptn-schema4 '{(:b :with ["ok"]) ?}) ;=> nil
  (m/explain ptn-schema4 '{(:b :with [3]) ?}) ;=>> (complement nil?)
  (m/explain ptn-schema4 '{(:c :with []) ?}) ;=> nil
  (m/explain ptn-schema4 '{(:a :batch [[3, :foo] [4, :bar]]) ?}) ;=> nil
  
  ;;with pattern can nested
  (def ptn-schema5 (pattern-schema-of [:map [:a [:=> [:cat :int] [:map [:b :string]]]]]))
  (m/explain ptn-schema5 '{(:a :with [3]) {:b ?}}) ;=> nil
  
  ;;for with pattern, its return type will be checked
  (m/explain ptn-schema5 '{(:a :with [3]) {(:b :not-found 5) ?}}) ;=>> {:errors #(= 1 (count %))}
  (m/explain ptn-schema5 '{(:a :with [3]) {(:b :not-found "ok") ?}}) ;=> nil
  
  ;;multiple options check
  (m/explain ptn-schema5 {(list :a :not-found str :with [:ok])
                          {(list :b :not-found 4) '?}}) ;=>> {:errors #(= 2 (count %))}
  
  ;;batch result testing
  (m/explain ptn-schema5 '{(:a :batch [[3] [2]]) {(:b :not-found "ok") ?}}) ;=> nil
  
  (def ptn-schema6
    (pattern-schema-of
     [:sequential
      [:map
       [:name :string]
       [:op [:=> [:cat :int] :int]]]]))
  (m/explain ptn-schema6 '[{:name "squre" (:op :with [3]) ?}]) ;=> nil
  )

(defn check-pattern!
  "check `pattern` against `data-schema`, if not conform throwing an ExceptionInfo
   with ex-data of explain result."
  [data-schema pattern]
  (let [ptn-sch (pattern-schema-of data-schema)]
    (when-not (m/validate ptn-sch pattern) 
      (throw (ex-info "Wrong pattern" (m/explain ptn-sch pattern))))))

^:rct/test
(comment
  (check-pattern! nil 3) ;throws=>> some?
  (check-pattern! nil {}) ;=> nil
  )
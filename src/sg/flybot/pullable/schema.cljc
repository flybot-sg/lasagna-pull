; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
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

(defn- options-of
  "returns pullable pattern option for `schema`"
  [schema]
  (let [args-trans (fn [s]
                     (case (m/type s)
                       :cat (m/into-schema :tuple (m/properties s) (m/children s))
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
                 
           (#{:fn (m/type fn?) :any} t)
           (into [[:cat [:= :with] vector?]
                  [:cat [:= :batch] [:vector vector?]]])
                 
           (or (= :any t) (seq-type? t))
           (into [[:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]))))]]))

^:rct/test
(comment
  (def try-val #(-> % (options-of) (m/explain %2)))
  (try-val :int [:default 5]) ;=> nil
  (try-val :int [:default :ok]) ;=>> (complement nil?)  
  (try-val fn? [:with [3]]) ;=> nil 
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
    (m/schema [:or schema [:fn lvar?] fn?])))

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
             (cond-> (explainer x' in acc)
               (not continue?) (reduced)))
           acc explainers))))))

(defn pattern-map-schema
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
; Public

(defn pattern-schema-of 
  "returns a pattern schema for given `data-schema`, default to general map or
   sequence of general maps."
  ([]
   (pattern-schema-of nil))
  ([data-schema]
   (m/walk
    (or data-schema [:or
                     [:map-of :any :any]
                     [:sequential [:map-of :any :any]]])
    (m/schema-walker
     (fn [sch]
       (let [t (m/type sch)]
         (cond
           (= :map t)
           (-> sch (pattern-map-schema))

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
                    [:? [:alt [:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]]])
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
  (m/validate ptn-schema2 '{:a {(:b :default :ok) ?}}) ;=> false
  ;;disallow directly fetch nesting
  (m/explain ptn-schema2 '{:a ?}) ;=>> (complement nil?)
  
  ;;sequential pattern
  (def ptn-schema3 (pattern-schema-of [:sequential [:map [:a :string]]]))
  (m/explain ptn-schema3 '[{:a ?} ?x :seq [1 2]]) ;=> nil
  
  ;;with pattern can pick up function schema
  (def ptn-schema4 (pattern-schema-of [:map
                                       [:a [:=> [:cat :int :keyword] :int]]
                                       [:b [:=> [:catn [:c :string]] :int]]]))
  (m/explain ptn-schema4 '{(:a :with [3 :foo]) ?}) ;=> nil
  (m/explain ptn-schema4 '{(:a :with [:ok :ok]) ?}) ;=>> (complement nil?)
  (m/explain ptn-schema4 '{(:b :with ["ok"]) ?}) ;=> nil
  (m/explain ptn-schema4 '{(:b :with [3]) ?}) ;=>> (complement nil?)
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
  
  )
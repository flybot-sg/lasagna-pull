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

(let [types #{:fn :function :=> (m/type fn?) :any}]
  (defn- func-type?
    "predict `x` type is a function type"
    [t]
    (boolean (types t))))

(defn- mark-ptn
  "mark a malli `schema` is a pullable pattern"
  [schema]
  (mu/update-properties schema assoc ::pattern? true))

(defn- ptn? 
  "predict if a malli schema is a pullable pattern"
  [schema]
  (-> (m/properties schema) ::pattern?))

(defn- options-of
  "returns pullable pattern option for `schema`"
  [schema]
  [:cat
   [:*
    (into
     [:alt]
     (let [t (m/type schema)]
       (cond-> [[:cat [:= :default] schema]
                [:cat [:= :not-found] schema]
                [:cat [:= :when] fn?]]
         (func-type? t)
         (into [[:cat [:= :with] vector?]
                [:cat [:= :batch] [:vector vector?]]])
         (or (= :any t) (seq-type? t))
         (into [[:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]))))]])

^:rct/test
(comment
  (def try-val #(-> % (options-of) (m/explain %2)))
  (try-val :int [:default 5]) ;=> nil
  (try-val :int [:default :ok]) ;=>> (complement nil?)  
  (try-val fn? [:with [3]]) ;=> nil 
  )

(defn- entries-collector
  "collect information on entries of a malli map schema returns a pair:
   entries, option schema map: key to schema explainer"
  [entries]
  (reduce 
   (fn [rslt [key props child]]
     (-> rslt
         (update 0 conj [key 
                         (assoc props :optional true)
                         [:or child [:fn lvar?] [:and fn? [:=> [:cat child] :boolean]]]])
         (update 1 conj (->> [key (when-not (ptn? child) (options-of child))]))))
   [[] {}] entries))

^:rct/test
(comment
  (entries-collector [[:a {} :int] [:b {} :keyword]]) ;=>> [[[:a {:optional true} ...] [:b {:optional true} ...]] {}]
  )

(defn- pattern-explainer
  [schema path]
  (let [keyset (m/-entry-keyset (m/-entry-parser schema))
        m-error (fn [path in schema value type] {:path path, :in in, :schema schema, :value value, :type type}) 
        normalizer (fn [m] (into {} (for [[k v] m] (if (list? k) [(first k) [v (rest k)]] [k [v]]))))
        explainers
        (-> (m/-vmap
             (fn [[key {option-schema ::options} schema]]
               (let [path (conj path key)
                     option-explainer (m/-explainer option-schema path)
                     explainer (m/-explainer schema path)]
                 (fn [x in acc]
                   (if-let [[_ [v o]] (find x key)]
                     (cond->> acc
                       (seq o)
                       (option-explainer o (conj in key))
                       true
                       (explainer v (conj in key)))
                     acc))))
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
             (explainer x' in acc))
           acc explainers))))))

(defn- pattern-entry-parser 
  [entry-parser]
  (reify
    m/EntryParser
    (-entry-keyset [_] (m/-entry-keyset entry-parser))
    (-entry-children 
      [_]
      (for [[k props schema] (m/-entry-children entry-parser)]
        [k (assoc props ::options (m/schema (options-of schema))) 
         (m/schema [:or schema [:fn lvar?] fn?])]))
    (-entry-entries [_] (m/-entry-entries entry-parser))
    (-entry-forms [_] (m/-entry-forms entry-parser))))

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
       (let [entry-parser (pattern-entry-parser (m/-entry-parser map-schema))]
         ^{:type ::schema}
         (reify
           m/AST
           (-to-ast [this _] (m/-entry-ast this (m/-entry-keyset entry-parser)))
           m/Schema
           (-explainer [this path]
             (pattern-explainer this path)) 
           (-validator [this] (fn [x] (-> x ((m/-explainer this []) [] []) seq boolean not)))
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
                   (mark-ptn))
               sch))

           :else sch)))))))

^:rct/test
(comment
  (def ptn-schema (pattern-schema-of (m/schema [:map [:a :int]])))
  (m/explain ptn-schema '{:a ?}) ;=> nil
  (m/explain ptn-schema '{(:a) ?}) ;=> nil
  (m/explain ptn-schema '{(:a :default 0) ?});=> nil  (m/explain ptn-schema '{(:a default :ok) ?}) ;=>> (complement nil?)

  (def ptn-schema2 (pattern-schema-of [:map [:a [:map [:b :int]]]]))
  (m/explain ptn-schema2 '{:a {:b :ok}}) ;=>> (complement nil?)
  (m/explain ptn-schema2 '{:a {(:b :default 0) ?}}) ;=> nil

  (m/validate ptn-schema2 '{:a {(:b :default :ok) ?}}) ;=> false
  )
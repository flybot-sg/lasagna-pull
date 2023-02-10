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

(defn- seq-type?
  "predict `x` type is a sequential type"
  [t]
  (boolean (#{:vector :sequential :set vector? set?} t)))

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
         (#{:fn fn? :=> :any} t)
         (into [[:cat [:= :with] vector?]
                [:cat [:= :batch] [:vector vector?]]])
         (or (= :any t) (seq-type? t))
         (into [[:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]))))]])

^:rct/test
(comment
  (def schema :int)
  (def try-val #(-> schema (options-of) (m/explain %)))
  (try-val 3) ;=> nil
  (try-val '?) ;=> nil
  (try-val ['? :default 5]) ;=> nil
  (try-val ['? :default :ok]) ;=>> (complement nil?) 
  )

(defn- entry-updater
  "update each `map-entry` of a malli map schema"
  [map-entry]
  (let [[key props child] map-entry]
    [key 
     (assoc props :optional true
            ::with-options (when-not (ptn? child) (options-of child)))
     [:or child [:fn lvar?]]]))

(defn- explain-options 
  [map-schema m path]
  (map (juxt first #(some-> % second ::with-options (m/-explainer path))) (m/children map-schema)))

(comment
  (explain-options [:map [:a :int] [:b :string]] {} [])
  )

(defn- pattern-map-schema
  "delegated schema"
  [map-schema]
  (let [extract-key (fn [k] (if (list? k) (first k) k))
        transform-m #(->> % (map (fn [[k v]] [(extract-key k) v])) (into {}))]
    (reify m/IntoSchema
      (-type [_] :pattern-map)
      (-into-schema
       [this properties children options]
       ^{:type ::m/schema}
       (reify m/Schema
         (-properties [_] properties)
         (-form [_] [:pattern-map (m/-form map-schema)])
         (-parent [_] this)
         (-options [_] options)
         (-children [_] children)

         (-walk [_ p1 p2 p3]  (m/-walk map-schema p1 p2 p3))
         (-validator
           [_]
           (fn [m]
             ((m/-validator map-schema) (transform-m m))))
         (-explainer
           [_ path]
           (fn [m in msgs]
             (let [map-errors ((m/-explainer map-schema path) (transform-m m) in msgs)] 
               map-errors))))))))

;;-------------------------------
; Public

(defn pattern-schema-of 
  "returns a pattern schema for given `data-schema`, default to general map or
   sequence of general maps."
  ([]
   (pattern-schema-of
    [:or 
     [:map-of :any :any]
     [:sequential [:map-of :any :any]]]))
  ([data-schema]
   (m/walk
    data-schema
    (m/schema-walker
     (fn [sch]
       (let [t (m/type sch)]
         (cond
           (= :map t)
           (-> sch
               (mu/transform-entries #(map entry-updater %))
               (mu/update-properties assoc :closed true)
               (pattern-map-schema)
               (mark-ptn))

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

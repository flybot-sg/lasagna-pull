; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
  (:require [malli.core :as m]
            [malli.util :as mu]
            [malli.dev.pretty :as mp]))
 
(defn lvar?
  [x]
  (and (symbol? x) (re-matches #"\?.*" (str x))))

(defn- seq-type?
  "predict `x` type is a sequential type"
  [t]
  (#{:vector :sequential :set vector? set?} t))

(defn- mark-ptn
  [schema]
  (assoc schema ::pattern? true))
(defn- ptn? 
  [schema]
  (-> (m/properties schema) ::pattern?)) ;;check if a schema is a pattern

(defn- options-of
  [sch]
  [:or 
   sch ;;filter
   [:fn lvar?]
   [:cat 
    [:fn lvar?]
    [:*
     (into
      [:alt]
      (let [t (m/type sch)]
        (cond-> [[:cat [:= :default] sch]
                 [:cat [:= :not-found] sch]
                 [:cat [:= :when] fn?]]
          (#{:fn fn? :=> :any} t)
          (into [[:cat [:= :with] vector?]
                 [:cat [:= :batch] [:vector vector?]]])
          (or (= :any t) (seq-type? t))
          (into [[:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]))))]]])

(defn- entry-updater
  [map-entry]
  (let [[key props child] map-entry]
    [key (assoc props :optional true) 
     (cond-> child (not (ptn? child)) (options-of))]))

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
               (mu/update-properties mark-ptn))

           (= :map-of t)
           (-> sch
               (mu/transform-entries
                (fn [[key-type val-type]]
                  (let [vector-ptn [:or val-type [:fn lvar?] (options-of val-type)]]
                    [key-type vector-ptn])))
               (mu/update-properties mark-ptn))

           (and (seq-type? t) (seq (m/children sch)))
           (let [x (-> sch m/children first)]
             (if (ptn? x)
               (-> [:cat 
                    x 
                    [:? [:fn lvar?]] 
                    [:? [:alt [:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]]]
                   (mu/update-properties mark-ptn))
               sch))

           :else sch)))))))

^:rct/test
(comment
  (require '[malli.dev.pretty :as mp]) 
  (-> [:map-of :any :any] (pattern-schema-of) (mp/explain '{:a ? :b even?}));=> nil
  (-> [:map [:a :int] [:b :string]] (pattern-schema-of) (mp/explain '{:a ? :b "ok"})) ;=> nil
  (-> [:map [:a :int] [:b :string]] (pattern-schema-of) (mp/explain '{:a ? :b 6})) ;=>> (not nil?)
  (-> [:map-of :any :any] (pattern-schema-of) (mp/explain '[{:a ? :b even?}])) 
  (-> [:map [:a [:map [:b :int]]]] (pattern-schema-of) (mp/explain {:a {:b '?}})) 
  (-> [:sequential [:map [:a :int]]] (pattern-schema-of) (mp/explain '[{:a ?} ?x :seq [1 5]]))
  )
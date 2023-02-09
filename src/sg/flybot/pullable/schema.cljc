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

#trace
(defn options-of
  [sch]
  [:or
   ::lvar
   sch
   [:cat ::lvar
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

(defn entry-updater
  [map-entry]
  (let [[key props child] map-entry]
    [key (assoc props :optional true) 
     (options-of child)]))

(defn pattern-schema-of 
  "returns a pattern schema for given `data-schema`, default to general map or
   sequence of general maps."
  ([]
   (pattern-schema-of
    [:map-of :any :any]))
  ([data-schema]
   (let [fixed {::lvar [:fn lvar?]
                ::seq-args [:cat
                            #_[:? ::lvar]
                            #_[:? [:cat [:= :seq] [:vector {:min 1, :max 2} :int]]]]}
         merger #(update % :registry merge fixed)
         mark-ptn #(assoc % ::pattern? true) ;;mark a schema is a pattern
         ptn? #(-> (m/properties %) ::pattern?) ;;check if a schema is a pattern
         data-schema (mu/update-properties data-schema merger)]
     (m/walk
      data-schema
      (m/schema-walker
       #trace
       (fn [sch]
         (let [t (m/type sch)]
           (cond
             (= :map t) 
             (-> sch
                 (mu/transform-entries #(map entry-updater %))
                 (mu/update-properties assoc :closed true)
                 (mu/update-properties mark-ptn))
             
             (= :map-of t)
             #trace
              (-> sch 
                  (mu/transform-entries
                   (fn [[key-type val-type]]
                     (let [vector-ptn [:or val-type ::lvar (options-of val-type)]]
                       [key-type vector-ptn])))
                  (mu/update-properties mark-ptn))
             
             (and (seq-type? t) (seq (m/children sch)))
             (let [x (-> sch m/children first)]
               (cond-> sch (ptn? x) (-> [:cat t x] (mu/update-properties mark-ptn))))
             
             :else sch))))))))

^:rct/test
(comment
  (require '[malli.dev.pretty :as mp]) 
  (-> [:map-of :any :any] (pattern-schema-of) (mp/explain '{:a ? :b even?}));=> nil
  (-> [:map [:a :int] [:b :string]] (pattern-schema-of) (mp/explain '{:a ? :b "ok"})) ;=> nil
  (-> [:map [:a :int] [:b :string]] (pattern-schema-of) (mp/explain '{:a ? :b 6})) ;=>> (not nil?)
  (-> [:map-of :any :any] (pattern-schema-of) (mp/explain '[{:a ? :b even?}])) 
  (-> [:map [:a [:vector :int]]] (pattern-schema-of) (mp/explain {:a '?})) 
  )
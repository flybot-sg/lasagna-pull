; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
  (:require [malli.core :as m]))

(defn lvar?
  [x]
  (and (symbol? x) (re-matches #"\?.*" (str x))))

(def general-pattern-registry
  "general schema registry"
  {::option   [:alt
               [:cat [:= :default] :any]
               [:cat [:= :not-found] :any]
               [:cat [:= :when] fn?]
               [:cat [:= :seq] vector?]
               [:cat [:= :with] vector?]
               [:cat [:= :batch] vector?]]
   ::key      [:orn
               [:k :keyword]
               [:k-with [:catn
                         [:k :keyword]
                         [:options [:* ::option]]]]]
   ::var      [:orn
               [:var   [:= '?]]
               [:named [:fn lvar?]]]
   ::val      [:orn
               ::var
               [:nested [:ref ::pattern]]
               [:filter :any]]
   ::vector   [:map-of ::key ::val]
   ::seq      [:cat
               ::vector
               [:?
                [:alt
                 [:= '?]
                 [:fn lvar?]]]
               [:* ::option]]
   ::pattern  [:or
               ::vector
               ::seq]})

(defn update-child
  "Adds pattern info to the given schema `child` when applicable.
   i.e. [:a {:min 1} :int] => [:a {:min 1} [:or ::var :int]]"
  [child] 
  (if (vector? child)
    (let [[k o v] child
          v       (if (m/schema? v) (m/form v) v)]
      (cond
        (some #{k} [:map :vector :sequential :set])
        child

        (and (vector? v) (sequential? (last v)))
        child

        :else
        (if o
          [k o [:or ::var v]]
          [k [:or ::var v]])))
    child))

(defn combine-data-pattern
  "Walks through the `data-schema` and updates it with pattern registry when applicable."
  [data-schema]
  (m/walk
   (update-in data-schema [1 :registry] (fnil merge {}) general-pattern-registry)
   (fn [schema _ children _]
     (let [children (if (and (not= :schema (m/type schema))
                             (vector? (m/form schema)))
                      (map update-child children)
                      children)]
       (m/into-schema
        (m/type schema) (m/properties schema) children (m/options schema))))))

(defn pattern-validator
  "returns a function which can validate query pattern.
   - `data-schema`: user provided schema for data"
  ([data-schema]
   (m/validator
    (if data-schema
      (combine-data-pattern data-schema)
      [:schema
       {:registry general-pattern-registry}
       ::pattern]))))

(comment
  ((pattern-validator nil) '{:a 'a (:b :with [3]) {:c ?a}}))
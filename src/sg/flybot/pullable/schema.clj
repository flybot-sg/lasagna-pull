; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
  (:require [malli.core :as m]
            [malli.util :as mu]))

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
  "Adds pattern info to the given `child` when applicable.
   `options` contains the required registries notably.
   i.e. [:a {:min 1} :int] => [:a {:min 1} [:or ::var :int]]"
  [child options]
  (if (vector? child)
    (let [[k o v] child]
      (if (or (some #{k} [:vector :sequential :set])
              (mu/find-first v (fn [sch _ _] (= :map (m/type sch))) options))
        child
        [k o [:or ::var v]]))
    child))

(defn combine-data-pattern
  "Walks through the `data-schema` and updates it with pattern registry when applicable."
  [data-schema] 
  (m/walk
   data-schema
   (fn [schema _ children options]
     (let [children (if (m/children schema)
                      (map #(update-child % options) children)
                      children)]
       (m/into-schema
        (m/type schema) (m/properties schema) children options)))
   (m/options data-schema)))

(defn to-vector-syntax
  "Takes a schema syntax and converts it to the vector syntax such as:
   [:schema properties children]"
  [syntax]
  (let [v-schema (if (or (vector? syntax) (m/schema? syntax))
                   (m/schema syntax)
                   (m/from-ast syntax))]
    (if (= :schema (m/type v-schema))
      v-schema
      [:schema nil v-schema])))

(defn pattern-validator
  "returns a function which can validate query pattern.
   - `data-schema`: user provided schema for data"
  [data-schema]
  (m/validator
   (if data-schema
     (let [schema        (-> data-schema to-vector-syntax m/children first)
           data-registry (-> data-schema m/properties :registry)]
       (combine-data-pattern
        [:schema
         {:registry ((fnil merge {}) data-registry general-pattern-registry)}
         [:and ::pattern schema]]))
     [:schema
      {:registry general-pattern-registry}
      ::pattern])))

(comment
  ((pattern-validator nil) '{:a ?a (:b :with [3]) {:c ?c}})
  ((pattern-validator [:schema nil [:map [:a :int] [:b [:map [:c :string]]]]])
   {:a '?a :b {:c '?c}}))
; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
  (:require [malli.core :as m]
            [malli.util :as mu]
            [malli.transform :as mt]))

;;; Internal utility functions

(defn lvar?
  [x]
  (and (symbol? x) (re-matches #"\?.*" (str x))))

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

(defn normalize-properties
  "Walk the schema and make sure all subschemas respect the format:
   [type properties & children]
   by adding nil for properties when not specified."
  [schema]
  (m/walk
   schema
   (fn [schema _ children _]
     (if (vector? (m/form schema))
       (into [(m/type schema) (m/properties schema)] children)
       (m/form schema)))))

;;; General pattern

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
   ::seq-args [:cat 
               [:?
                [:alt
                 [:= '?]
                 [:fn lvar?]]]
               [:* ::option]]
   ::seq      [:cat
               ::vector
               ::seq-args]
   ::pattern  [:or
               ::vector
               ::seq]})

(def general-pattern-schema
  [:schema
   {:registry general-pattern-registry}
   ::pattern])

(def general-pattern-validator
  (m/validator general-pattern-schema))

;;; Client pattern

(defn update-child
  "Adds pattern info to the given `child` when applicable.
   `options` contains the required registries notably.
   i.e. [:a {:min 1} :int] => [:a {:min 1} [:or ::var :int]]"
  [child options] 
  (let [child (if (m/schema? child)
                (-> child normalize-properties)
                child)]
    (if (vector? child)
      (let [[k o & v] child
            v1 (if (m/schema? (first v)) (normalize-properties (first v)) (first v))] 
        (cond
          (and (vector? v1)
               (some #{(first v1)} [:vector :sequential :set])
               (mu/find-first v1 (fn [sch _ _] (= :map (m/type sch))) options))
          [k o [:or v1 [:cat (last v1) ::seq-args]]]

          (and (some #{k} [:vector :sequential :set])
               (mu/find-first v1 (fn [sch _ _] (= :map (m/type sch))) options))
          [:or child [:cat v1 ::seq-args]]

          (some #{k} [:map :vector :sequential :set])
          child

          (mu/find-first v1 (fn [sch _ _] (= :map (m/type sch))) options)
          child

          :else
          [k o [:or ::var v1]]))
      child)))

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

(def keys-transformer
  "Transforms pattern's keys into simple keyword keys.
   i.e. {'(:a :with [3]) '?a} => {:a '?a}"
  (mt/key-transformer
   {:encode (fn [k] (if (sequential? k) (first k) k))}))

(defn keys-encoder
  "Returns an encoder that uses `keys-transformer` on the pattern."
  [data-schema]
  (m/encoder data-schema keys-transformer))

(defn client-pattern-schema
  [data-schema]
  (combine-data-pattern
   (let [inner-schema  (-> data-schema to-vector-syntax m/children first)
         data-registry (-> data-schema m/properties :registry)]
     [:schema
      {:registry ((fnil merge {}) data-registry general-pattern-registry)}
      inner-schema])))

(defn client-pattern-validator
  [data-schema]
  (let [encoder       (keys-encoder data-schema)
        validator     (m/validator (client-pattern-schema data-schema))]
    (comp validator encoder)))

;;; Validator of pattern

(defn pattern-validator
  "Returns a function which can validate a query pattern.
   Validates pattern agains 2 schemas:
   - general pattern schema: generic validation (options in keys, selectors formats etc.)
   - client pattern schema: when `data-schema` is provided, runs furhter validations combining the data and pattern format when applicable
   (proper keys, proper filters, etc.)
   The function returns the pattern if it is valid, else returns pure malli error."
  [data-schema]
  (fn [pattern] 
    (cond
      (not (general-pattern-validator pattern))
      {:error {:cause     "Invalid general pattern syntax"
               :type      :general-pattern-syntax
               :malli-err (mu/explain-data general-pattern-schema pattern)}}

      (and data-schema
           (not ((client-pattern-validator data-schema) pattern)))
      {:error {:cause     "Invalid client pattern data"
               :type      :client-pattern-data
               :malli-err (mu/explain-data data-schema (m/encode data-schema pattern keys-transformer))}}

      :else
      pattern)))

(comment
  ((pattern-validator nil) '{:a ?a (:b :with [3]) {:c ?c}})
  ((pattern-validator [:schema nil [:map [:a :int] [:b [:map [:c :string]]]]])
   {'(:a :with [3]) '?a :b {:c '?c}}))
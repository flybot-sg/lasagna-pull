; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
  (:require [clojure.walk :refer [postwalk]]
            [malli.core :as m]
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
               [:filter [:or :int :double :boolean :keyword :uuid :string fn?]]]
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

(defn pattern-validator
  "returns a function which can validate query pattern.
   - `data-schema`: user provided schema for data"
  ([data-schema]
   (m/validator
    (if data-schema
      (throw (UnsupportedOperationException. "Not implemented yet"))
      [:schema
       {:registry general-pattern-registry}
       ::pattern]))))

(defn add-pattern
  "Adds pattern info to the shcema element `el` when applicable."
  [el]
  (if (vector? el)
    (let [[k v] el]
      (cond
        (some #{k} [:map :vector :sequential :set])
        el

       (and (vector? v) (sequential? (last v)))
        el

        :else
        [k [:or ::var v]]))
    el))

(defn remove-malli-options
  "Malli syntax contains optional maps such as [:map {:closed true} [:a :int]].
   Removes the option map from the schema element `el`."
  [el]
  (if (and (vector? el) (map? (second el)))
    (let [[a _ & r] el]
      (vec (concat [a] r)))
    el))

(def pattern-registry
  "Combinations of malli default registries and our custom registry."
  (merge (m/default-schemas) (mu/schemas) general-pattern-registry))

(defn merge-schemas
  "Merges 2 schemas."
  [sch1 sch2]
  (m/deref
   (m/schema
    [:merge sch1 sch2]
    {:registry pattern-registry})))

(defn combine-data-pattern
  "Enhances the given `data-schema` with the pattern schema.
   The malli option maps are first removed to avoid being walked by the pattern.
   Then the pattern schema is being combined with the data-schema.
   Finally, the malli option maps are added back via merging with original data-schema."
  [data-schema]
  (merge-schemas 
   data-schema
   (->> data-schema
        (postwalk remove-malli-options)
        (postwalk add-pattern))))

(comment
  ((pattern-validator nil) '{:a ? (:b :with [3]) {:c ?a}})
  (combine-data-pattern
   [:map [:a :int] [:b :string]]) 

  (m/validate
   (combine-data-pattern
    [:map [:a :int] [:b :string]])
   {:a '?a :b "ok"}
   {:registry pattern-registry}))
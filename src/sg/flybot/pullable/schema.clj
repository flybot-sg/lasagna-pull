; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
  (:require
   [malli.core :as m]))

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
   ::val      [:orn
               [:var    [:= '?]]
               [:named  [:fn lvar?]]
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

(comment
  ((pattern-validator nil) '{:a ? :b {:c ?a}})
  )
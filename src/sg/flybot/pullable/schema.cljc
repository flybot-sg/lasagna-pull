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

(defn options-of
  [sch]
  [:or sch
   ::lvar
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
          (#{:vector :sequential :set vector? set? :any} t)
          (into [[:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]))))]]])

(defn entry-updater
  [map-entry]
  (let [[key props child] map-entry]
    [key (assoc props :optional true) 
     (options-of child)]))

(defn schema-of 
  [data-schema]
  (let [fixed {::lvar [:fn lvar?]
               ::seq-args [:cat
                           #_[:? ::lvar]
                           #_[:? [:cat [:= :seq] [:vector {:min 1, :max 2} :int]]]]}
        merger #(update % :registry merge fixed)
        data-schema (mu/update-properties data-schema merger)]
    (m/walk
     data-schema
     (m/schema-walker
      (fn [sch]
        (case (m/type sch)
          :map 
          (-> sch 
              (mu/update-properties assoc :closed true)
              (mu/transform-entries #(map entry-updater %)))
          
          :map-of
          (mu/transform-entries
           sch
           (fn [[key-type val-type]]
             (let [vector-ptn [:or val-type ::lvar (options-of val-type)]]
               [key-type vector-ptn])))
          
          sch))))))

^:rct/test
(comment
  (require '[malli.dev.pretty :as mp])
  (-> [:map-of :any :any] (schema-of) (mp/explain '{:a ? :b even?}));=> nil
  (-> [:map [:a :int] [:b :string]] (schema-of) (mp/explain '{:a ? :b "ok"})) ;=> nil
  (-> [:map [:a :int] [:b :string]] (schema-of) (mp/explain '{:a ? :b 6})) ;=>> (not nil?)
  (-> [:map-of :any :any] (schema-of) (mp/explain '[{:a ? :b even?}]))
  )
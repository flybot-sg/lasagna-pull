; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern and data validation with Malli schema."
  (:require [clojure.walk :refer [postwalk]]
            [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]))

(defn valid-symbol?
  "Returns true if `p` is a lvar or ?"
  [p]
  (and (symbol? p) (re-matches #"\?.*" (name p))))

(defn transform-key
  "Adapts map keys formats to make it malli schema friendly.
   i.e. (:a :when even? :not-found 0) => [[:a nil] [:when odd?] [:not-found 0]]."
  [mk]
  (let [[k & opts] (if (sequential? mk) mk [mk])]
    (->> (concat [k nil] opts)
         (partition 2)
         (map vec)
         (into []))))

(defn transform-all-keys
  "Recursively transforms all map keys to malli friendly keys."
  [m]
  (let [f (fn [[k v]] [(transform-key k) v])]
    ;; only apply to maps
    (postwalk (fn [x] (if (map? x)
                        (into {} (map f x))
                        x))
              m)))

(def sch-pattern-keys
  "Malli schema for the different post-processors in the keys.
   Expects individual post-processors with format [proc arg]
   i.e. [:not-found 0]"
  [:multi {:dispatch first}
   [:when [:tuple [:= :when] fn?]]
   [:not-found [:tuple [:= :not-found] :any]]
   [:with [:tuple [:= :with] vector?]]
   [:batch [:tuple [:= :batch] [:vector vector?]]]
   [:watch [:tuple [:= :watch] fn?]]
   [::m/default [:tuple :keyword :nil]]])

(defn sch-pattern-seq
  "Malli schema for the pattern eventually inside seq."
  [pattern]
  [:or 
    pattern 
    [:tuple pattern]
    [:tuple pattern [:maybe [:fn valid-symbol?]]]
    [:tuple pattern [:fn valid-symbol?] [:= :seq] [:sequential any?]]])

(def sch-pattern
  "Malli schema for the pattern."
  [:schema
   {:registry
    {::pattern
     [:map-of
      [:vector sch-pattern-keys]
      [:or 
       [:fn valid-symbol?] ;; '? or '?x 
       [:fn #(not (sequential? %))] ;; filters
       (sch-pattern-seq [:ref ::pattern])]]}}
   (sch-pattern-seq ::pattern)])

(defn validate*
  "Runs the `data` against the malli `schema`.
   The data is first transformed via `f-transform` before validation.
   `f-transform` is a 1arg function that takes the `data`.
   Returns the `data` if the transformed data is valid, else throws error."
  [data f-transform schema]
  (let [validator        (m/validator schema)
        transformed-data (f-transform data)]
    (if (validator transformed-data)
      data
      (throw
       (let [err (mu/explain-data schema transformed-data)]
         (ex-info (str (me/humanize err))
                  {:data         data
                   :data-checked transformed-data
                   :error        err}))))))

(defn validate-pattern
  "Returns the given `pattern` if it respects the malli schema `sch-pattern`, else throws error.
   The keys of the pattern are modified before validation to comply to malli schema syntax."
  [pattern]
  (validate* pattern transform-all-keys sch-pattern))

(defn validate-data
  "Returns the given `pulled-data` if it respects the given malli `schema`, else throws error.
   `pulled-data` is a vector such as [{:a 3} {'?a 3}].
   Only the first part is validated, such as {:a 3}."
  [pulled-data schema]
  (validate* pulled-data first schema))

(comment
  (m/validate sch-pattern {[[:a nil] [:not-found 3] [:when odd?]] '?})
  (validate-pattern [{(list :a :not-found 3 :when odd?) '?}]))
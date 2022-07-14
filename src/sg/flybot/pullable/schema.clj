; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
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

(defn validate-pattern
  "Runs the `pattern` against the malli schema.
   The pattern keys are first modified to allow malli schema validation.
   Returns the `pattern` if pattern is valid, else throws error."
  [pattern]
  (let [validator        (m/validator sch-pattern)
        pattern-new-keys (transform-all-keys pattern)]
    (if (validator pattern-new-keys)
      pattern
      (throw
       (let [err (mu/explain-data sch-pattern pattern-new-keys)]
         (ex-info (str (me/humanize err))
                  {:pattern          pattern
                   :pattern-new-keys pattern-new-keys
                   :error            err}))))))

(comment
  (m/validate sch-pattern {[[:a nil] [:not-found 3] [:when odd?]] '?})
  (validate-pattern [{(list :a :not-found 3 :when odd?) '?}]))
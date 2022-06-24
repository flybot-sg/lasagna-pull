; Copyright. 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable
  "Pull from data structure by using pattern.
   
   Pattern is a DSL in clojure data structure, specify how to extract
   information from data."
  (:require
   [sg.flybot.pullable.core :as core]
   [sg.flybot.pullable.pattern :as ptn]
   [malli.core :as m]
   [malli.error :as me]))

;;## Glue code 

(defn- pattern->query
  "Takes a function `f-named-var` to create named variable query, and `x`
   is the expression to specify whole query,
   returns a function takes named variable constructor."
  [f-named-var]
  (fn [x]
    (let [ctor-map {:fn     core/fn-query
                    :vec    core/vector-query
                    :seq    core/seq-query
                    :filter (fn [q v] (core/filter-query q (if (fn? v) v #(= % v))))
                    :named  (fn [q sym] ((f-named-var sym) q))
                    :join   core/join-query
                    :deco   (fn [q pp-pairs]
                              (core/decorate-query q pp-pairs))}
          [x-name & args] x]
      (if-let [f (get ctor-map x-name)]
        (apply f args)
        (ptn/pattern-error! "not understandable pattern" x)))))

(defn query
  "Returns a compiled query from `pattern`. A query can be used to extract information
   from data.
   
   A pattern is a clojure data structure, in most cases, a pattern looks like the data
   which it queries, for example, to query data `{:a {:b {:c 5}} :d {:e 2}}` to extract
   `:c`, `:e`, you might use a pattern `'{:a {:b {:c ?c}} :d {:e ?e}}`. By marking the
   information you are interested in by logic variable like `?c`, `?e`, or by anonymous
   variable `?`, you can get informatioon from the potentially massive data.

     - Map pattern: `{...}`, mimic the shape of the map to be queried
   
     - Filter pattern: If you specified a concrete value for a given key in a map, it works
       like a filter, i.e., pattern `{:a ? :b 1}` will return an empty map on data `{:a 1 :b 2}`.
       If you use same logical variabl in multiple places of a pattern, it has to be the
       same value, so if we can not satisfy this, the matching fails. i.e., 
       pattern `'{:a ?x :b ?x}` returns an empty map on data `{:a 2 :b 3}`.
   
     - Options: by enclosing a key in map pattern with a list `()`, and optional pairs of
       options, you can process value after a match or miss. i.e., pattern
       `{(:a :not-found ::ok) ?}` on data `{:b 5}` having a matching result of `{:a ::ok}`.
       Various options supported, and can be defined by multimethod of `core/apply-post`.
   
     - Sequence pattern: For sequence of maps, using `[]` to enclose it. Sequence pattern
       can have an optional variable and options. i.e., pattern 
       `'[{:a ?} ?]` on `[{:a 1} {:a 3} {}]` has a matching result of
       `[{:a 1} {:a 3} {}]`. "
  [pattern]
  (with-meta
    (fn [f-named-var]
      (ptn/->query (pattern->query f-named-var) pattern))
    {::compiled? true}))

(defn compile-query
  "If `query-or-pattern` is a pattern such as '{:a ?}, runs it to get the query,
   If it is a query such as (query '{:a ?}), returns it."
  [query-or-pattern]
  (if (some-> query-or-pattern meta ::compiled?)
    query-or-pattern
    (query query-or-pattern)))

(defn validate-data
  "Runs the pulled-data against a `malli` schema.
   Returns the pulled-data if it respects the schema, else throws error."
  [pulled-data schema]
  (if schema
    (let [validator (m/validator schema)
          data (first pulled-data)]
      (if (validator data)
        pulled-data
        (throw
         (let [err (m/explain schema data)]
           (ex-info (str (me/humanize err))
                    {:data pulled-data :error err})))))
    pulled-data))

(defn run
  "Given `data`, compile `pattern` if it has not been, run it, validate it, try to match with `data`.
   Returns a vector of matching result (same structure as data) and a map of logical
   variable bindings."
  ([query-or-pattern data]
   (run query-or-pattern data nil))
  ([query-or-pattern data schema]
   (-> (compile-query query-or-pattern)
       (core/run-bind data)
       (validate-data schema))))

(comment
  (run '{:a {:b {:c ?c}} :d {:e ?e}} {:a {:b {:c 5}} :d {:e 2}})
  (run {:a '?x :b '?x} {:a 2 :b 3})
  (run '[{(:a :not-found ::ok) ?} ?a] [{:a 1} {:a 3} {}])
  )
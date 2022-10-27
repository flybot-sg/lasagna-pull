; Copyright. 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable
  "Pull from data structure by using pattern.
   
   Pattern is a DSL in clojure data structure, specify how to extract
   information from data."
  (:require
   [sg.flybot.pullable.core :as core]
   [sg.flybot.pullable.pattern :as ptn]))

;;## Glue code 

(defn query
  "Returns a query function from `pattern`. A query function can be used to extract information
   from data. Query function takes `data` as its single argument, returns a vector of resulting
   data and output variable bindings in a map.
   
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
       `[{:a 1} {:a 3} {}]`. 
   
   Advanced arguments. Sometimes you need subqueries sharing information among them.
     - `query-wrapper` is function take a query as first argument and abitary arguments, returns a query.
     - `finalizer` is a function takes a map as argument, returns a map, it will be called at the end of a
       query running, the result map will be returned as the second of the returned pair.
   "
  ([pattern]
   (query pattern nil nil))
  ([pattern query-wrapper finalizer]
   (fn [data]
     (let [context (reify core/QueryContext
                     (-wrap-query [_ q args] (if query-wrapper (apply query-wrapper q args) q))
                     (-finalize [_ m] ((or finalizer identity) m)))
           q (ptn/->query (core/query-maker context) pattern ptn/filter-maker)]
       (core/run-bind q data)))))

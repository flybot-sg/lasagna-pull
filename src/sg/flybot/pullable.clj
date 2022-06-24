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

(defn- pattern->query
  "Takes a function `f-named-var` to create named variable query, and `x`
   is the expression to specify whole query,
   returns a function takes named variable constructor." 
  [ctx x]
  (let [ctor-map {:fn     core/fn-query
                  :vec    core/vector-query
                  :seq    core/seq-query
                  :filter (fn [q v ctx] (core/filter-query q (if (fn? v) v #(= v %)) ctx))
                  :named  (fn [q sym ctx] (core/named-query ctx sym q))
                  :join   core/join-query
                  :deco   (fn [q pp-pairs ctx]
                            (core/decorate-query q pp-pairs ctx))}
        [x-name & args] x]
    (if-let [f (get ctor-map x-name)]
      (apply f (conj (vec args) ctx))
      (ptn/pattern-error! "not understandable pattern" x))))

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
  (let [ctx (core/context)]
    (ptn/->query (partial pattern->query ctx) pattern)))

(defn run
  "Given `data`, compile `pattern` if it has not been, run it, try to match with `data`.
   Returns a vector of matching result (same structure as data) and a map of logical
   variable bindings."
  [query-or-pattern data]
  (core/run-bind
   (if (core/query? query-or-pattern)
     query-or-pattern
     (query query-or-pattern))
   data))

(comment
  (query '{:a ?})
  (run '{:a ?a :b ?a} {:a 3 :b 2}) 
  (run '{:a {:b {:c ?c}} :d {:e ?e}} {:a {:b {:c 5}} :d {:e 2}})
  (run {:a '?x :b '?x} {:a 2 :b 3})
  (run '[{(:a :not-found ::ok) ?} ?a] [{:a 1} {:a 3} {}])
  )
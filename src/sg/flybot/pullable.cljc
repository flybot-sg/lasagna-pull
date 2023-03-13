; Copyright. 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable
  "Pull from data structure by using pattern.
   
   Pattern is a DSL in clojure data structure, specify how to extract
   information from data." 
  (:require
   [sg.flybot.pullable.core :as core]
   [sg.flybot.pullable.pattern :as ptn]
   [sg.flybot.pullable.util :as util]))

;;## APIs

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
     - You can pass an additional `context` to it, which is a `QueryContext` instance made by
       `context-of` function."
  ([pattern]
   (query pattern nil))
  ([pattern context]
   (fn [data]
     (-> context
         core/query-maker
         (ptn/->query pattern ptn/filter-maker)
         (core/run-bind data)))))

(defn context-of
  "returns a query context of function `modifier` and function `finalizer`(default `clojure.core/identity`),
   this could be used to:
     - modify query result, the value returned by `modifier` will be in the query result.
     - collect information in all query results.
   Information on arguments:
   - `modifier`: returns a key-value pair which will contains in the query result. argurments are:
     - `args`: a vector that might passed to this.
     - `key-value`: a key-value pair returned by original query running result.
   - `finalizer`: accept a single map and returns any value which will be called when we've done querying."
  ([modifier]
   (context-of modifier identity))
  ([modifier finalizer]
   (core/context-of modifier finalizer)))

(defn run-query
  "Query `data` on `pattern`, returns a pair. See `query` function. Convinient for one-time use pattern."
  ([pattern data]
   (run-query query pattern data))
  ([f pattern data]
   ((f pattern) data)))

(defmacro qfn 
  "Define an anonymous query function (a.k.a qn)"
  [pattern & body]
  (let [args (-> (util/named-lvars-in-pattern pattern) vec)]
    `(let [q# (query ~pattern)]
       (fn [data#]
         (let [{:syms ~args} (q# data#)]
           ~@body)))))

^:rct/test
(comment
  ;;`run-query` is a convinient function over `query`
  (run-query '{:a ?} {:a 1 :b 2}) ;=> {& {:a 1}} 
  (macroexpand-1 '(qn [?a ?b] {:a ?a :b ?b} (+ ?a ?b)))
  ((qfn '{:a ?a :b ?b} (+ ?a ?b)) {:a 1 :b 2}) ;=> 3
  )
  

#?(:clj
   #_{:clj-kondo/ignore [:clojure-lsp/unused-public-var]}
   (defn query-of
     "returns an instrumented version of `pull/query` for `data-schema`" 
     ([data-schema]
      #_{:clj-kondo/ignore [:unresolved-symbol]}
      (util/optional-require 
       [sg.flybot.pullable.schema :as schema]
       #_{:clj-kondo/ignore [:unresolved-namespace]}
       (schema/instrument! data-schema query)
       (throw (ClassNotFoundException. "Need metosin/malli library in the classpath"))))))
   
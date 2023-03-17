```clojure
(ns introduction
  (:require [sg.flybot.pullable :as pull :refer [qfn]])) ;=> nil
```
# Lasagna-pull, query data from deep data structure
[![CI](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml/badge.svg)](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml)
[![Code Coverage](https://codecov.io/gh/flybot-sg/lasagna-pull/branch/master/graph/badge.svg)](https://codecov.io/gh/flybot-sg/lasagna-pull)
[![Clojars](https://img.shields.io/clojars/v/sg.flybot/lasagna-pull.svg)](https://clojars.org/sg.flybot/lasagna-pull)
[![CljDoc](https://cljdoc.org/badge/sg.flybot/lasagna-pull)](https://cljdoc.org/d/sg.flybot/lasagna-pull)
## Problem
 Let's see some data copied from deps.edn:
```clojure
(def data {:deps
           {:paths ["src"],
            :deps {},
            :aliases
            {:dev
             {:extra-paths ["dev"],
              :extra-deps
              {'metosin/malli {:mvn/version "0.10.2"},
               'com.bhauman/figwheel-main {:mvn/version "0.2.18"}}},
             :test
             {:extra-paths ["test"],
              :extra-deps
              {'lambdaisland/kaocha {:mvn/version "1.80.1274"}},
              :main-opts ["-m" "kaocha.runner"]}}}}) ;=> #'introduction/data
```

 In `clojure.core `we have `get `and `get-in `to extract information from a
 map and we are very familiar with them. However, if we have multiple pieces of
 information needs to be extracted from different location of the map, things
 are starting getting tricky.

```clojure
(let [global-path     (get-in data [:deps :paths])
      dev-extra-path  (get-in data [:deps :aliases :dev :extra-paths])
      test-extra-path (get-in data [:deps :aliases :test :extra-paths])]
  (concat global-path dev-extra-path test-extra-path)) ;=> ("src" "dev" "test")
```
 We have to call `get`/`get-in` multiple times manually.
## Bring Lasagna-pull in
 Using lasagna-pull, we can do it in a more intuitive way:
```clojure
((qfn '{:deps {:paths   ?global
               :aliases {:dev  {:extra-paths ?dev}
                         :test {:extra-paths ?test}}}}
      (concat ?global ?dev ?test))
 data) ;=> ("src" "dev" "test")
```
 `pull/qfn` returns a query function, which matches data using a Lasagna pattern.
 A lasagna pattern mimics your data, you specify the pieces of information
 that you interested by using logic variables, they are just symbols starting with
 a `?`.

 > :bulb: `qfn` scans your pattern, binding them then you can use them
 > directly in the function's body.

 Another frequent situation is selecting subset of data, we could use 
 `select-keys` to shrink a map, and `map` it over a sequence of maps.
 Lasagna-pull provide this with an implicit binding `&?`:
```clojure
((qfn '{:deps {:paths ? :aliases {:dev {:extra-paths ?}}}} &?)
 data) ;=> {:deps {:paths ["src"], :aliases {:dev {:extra-paths ["dev"]}}}}
```
 The unnamed binding `?` in the pattern is a placeholder, it will appear
 in `&?`.
### Sequence of maps

 The pattern to select inside the sequence of the maps just look like the data itself:
```clojure
(def person&fruits {:persons [{:name "Alan", :age 20, :sex :male}
                              {:name "Susan", :age 12, :sex :female}]
                    :fruits [{:name "Apple", :in-stock 10}
                             {:name "Orange", :in-stock 0}]
                    :likes [{:person-name "Alan" :fruit-name "Apple"}]}) ;=> #'introduction/person&fruits
```
 The pattern to select inside the sequence of maps just look like the data itself:
```clojure
((qfn '{:persons [{:name ?}]} &?)
 person&fruits) ;=> {:persons [{:name "Alan"} {:name "Susan"}]}
```
 by appending a logical variable in the sequence marking vector, we can capture
 a sequence of map.
```clojure
((qfn '{:persons [{:name ?} ?names]} (map :name ?names))
 person&fruits) ;=> ("Alan" "Susan")
```
### Filter a value

  Sometimes, we need filtering a map on some keys. It feels very intuitive to specific
  it in your pattern, let's find Alan's age:
```clojure
((qfn '{:persons [{:name "Alan" :age ?age}]} ?age)
 person&fruits) ;=> nil
```
 The success of above query is due to we filtered on `:name`, and there are 
 only one item in `:persons` has the value of "Alan."

### Using same named lvar multiple times to join

  If a named lvar bound more than one time, its value has to be the same, otherwise
  all matching fails. We can use this to achieve a join, let's find Alan's favorite fruit
  in-stock value:
```clojure
(def q2 (qfn '{:a ?x :b {:c ?c :d ?x}} [?c ?x])) ;=> #'introduction/q2
(q2 {:a 1 :b {:c 5 :d 1}}) ;=> [5 1]
(q2 {:a 1 :b {:c 5 :d 2}}) ;=> [nil nil]
```
 ## Pattern Options

 In addition to basic query, Lasagna-pull support pattern options, it is a decorator
 on the key part of a pattern, let you fine tune the query.
 An option is a pair, a keyword followed by its arguments.

 ### General options

 These options does not require a value in a specific type.

 #### `:not-found` option

 The easiest option is `:not-found`, it provides a default value if a value is not
 found.
```clojure
((qfn '{(:a :not-found 0) ?a} ?a)
 {:b 3}) ;=> 0
```
 #### `:when` option

 `:when` option has a predict function as it argument, a match only succeed when this
 predict fulfilled by the value.
```clojure
(def q (qfn {(list :a :when odd?) '?a} ?a)) ;=> #'introduction/q
(q {:a 3 :b 3}) ;=> 3
```
 This will failed to match, e.g. returns nil:
```clojure
(q {:a 2 :b 3}) ;=> nil
```
 > Note that the basic filter can also be thought as a `:when` option.

 You can combine options together, they will be applied by their order:
```clojure
((qfn {(list :a :when even? :not-found 0) '?} &?)
 {:a -1 :b 3}) ;=> {:a 0}
```
 ### Pattern options requires values be a specific type

 #### `:seq` option
 
 If you are about to match a big sequence, you might want to do pagination. 
 It is essential if your values are lazy (sequential).
```clojure
((qfn '{(:a :seq [5 10]) ?} &?)
 {:a (range 1000)}) ;=> {:a (5 6 7 8 9 10 11 12 13 14)}
```
 The design of putting options in the key part of the patterns, enable us
 to do nested query.
```clojure
(def range-data {:a (map (fn [i] {:b i}) (range 100))}) ;=> #'introduction/range-data
((qfn '{(:a :seq [90 5]) [{:b ?} ?bs]} (map :b ?bs))
 range-data) ;=> (90 91 92 93 94)
```
 #### `:with` and `:batch` options

 You may store a function as a value in your map, then when querying it, you
 can apply arguments to it, `:with` enable you to do it:
```clojure
(defn square [x] (* x x)) ;=> #'introduction/square
((qfn '{(:a :with [5]) ?a} ?a)
 {:a square}) ;=> 25
```
 And `:batch` will apply many times on it, as if it is a sequence:
```clojure
((qfn {(list :a :batch (mapv vector (range 100)) :seq [40 5]) '?a} ?a)
 {:a square}) ;=> (1600 1681 1764 1849 1936)
```
  These options not just for convinience, if the embeded functions invoked by 
  a query has side effects, these options could do 
  [GraphQL's mutation](https://graphql.org/learn/queries/#mutations).
```clojure
(def a (atom 0)) ;=> #'introduction/a
((qfn {(list :a :with [5]) '?a} ?a)
 {:a (fn [x] (swap! a + x))}) ;=> 5
```
 And the atom has been changed:
```clojure
@a ;=> 5
```
  ## Malli schema support
  Lasagna-pull has optional support for [malli](https://github.com/metosin/malli) schemas.
  In Clojure, by put malli in your dependencies, it automatically checks your data
  pattern when you make queries to make sure the syntax is correct.

  Your query patterns not only follow the rules of pattern, it also should conform 
  to your data schema. It is especially important when you want to expose your data to
  the external world.

  `with-data-schema` macro let you include your customized data schema (applies to your data),
  then in the body, Lasagna-pull do a deeper check for the pattern. Try to find if 
  the query pattern conforms to the meaning of data schema (in terms of each value
  and your data structrue).
```clojure
(defmacro try! [& body] `(try ~@body (catch Throwable e# #:error{:msg (ex-message e#)}))) ;=> #'introduction/try!
(def my-data-schema [:map [:a :int] [:b :keyword]]) ;=> #'introduction/my-data-schema
(try!
 (pull/with-data-schema my-data-schema
   (qfn '{:c ?c} ?c))) ;throws=>#:error{:type clojure.lang.Compiler$CompilerException, :message "Syntax error compiling at (0:0).", :data #:clojure.error{:phase :compile-syntax-check, :line 0, :column 0, :source "NO_SOURCE_PATH"}}
```
  The above throws an exception! Because once you specified a data schema, you only
  allow users to query values documented in the schema (i.e. closeness of map). 
  Since `:c` is not included in the schema, querying `:c` is invalid.

 Lasagna-pull try to find all schema problems:
```clojure
(try!
 (pull/with-data-schema my-data-schema
   (qfn '{(:a :not-found "3") ?} &?))) ;throws=>#:error{:type clojure.lang.Compiler$CompilerException, :message "Syntax error compiling at (0:0).", :data #:clojure.error{:phase :compile-syntax-check, :line 0, :column 0, :source "NO_SOURCE_PATH"}}
```

  The above code also triggers an exception, because `:a` is an `:int` as in the data schema,
  while you provide a `:not-found` pattern option value which is not an integer.

  ## Real world usage and some history
  
  In Flybot, we use [fun-map](https://github.com/robertluo/fun-map) extensively.
  In a typical server backend, we put everything mutable into a single map called
  `system`, including atoms, databases even web server itself. The fun-map library
  manages the lifecycles of these components, injects dependencies among them.

  Then we add database queries into `system`, they relies on databases, so the 
  database value (we use Datomic) or connection (if using SQL databases) will be
  injected by fun-maps `fnk`, and while developing, we can easyly replace the
  db connection by mocked values.

  Most of the queries not just require database itself, also input from user requests,
  so in the ring handler (of course, in `system`), we `merge` http request to 
  `system`, this merged version then will be used to be the source of all possible
  data.

  The next problem is how to let user to fetch, we are not satisfied with conventional
  REST style, GraphQL is good, but it adds too much unneccesary middle layers in my
  opinion, for example, we need to write many resolvers in order to glue it to backend
  data. 
  The first attempt made was [juxt pull](https://github.com/juxt/pull),
  I contributed to the 0.2 version, used it in some projects, allowing users to 
  construct a data pattern (basic idea and syntax is similar to
  [EQL](https://github.com/edn-query-language/eql)) to pull data from the request
  enriched `system`.

  However, the syntax of pull does not go very well with deep and complex data
  structures (because database information also translated into fields), and it 
  just returns a trimmed version of the original map, users often need to walk the
  returned value again to find out the pieces of information by themselves.

  Lasagna-pull introduces a new designed query pattern in order to address
  these problems.

 ## Development

 - Run `clojure -T:build ci`.
 - This project's uses a lot of [rct tests](https://github.com/robertluo/rich-comment-tests).

 ## About this README

 This file is generated by `notebook/introduction.clj` 
 using [clerk-doc](https://github.com/robertluo/clerk-doc).

 ## License
 Copyright. © 2022 Flybot Pte. Ltd.
 Apache License 2.0, http://www.apache.org/licenses/

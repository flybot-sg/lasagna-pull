```clojure
(ns introduction
  (:require [sg.flybot.pullable :as pull :refer [qfn]])) ;=> nil
```

# Lasagna-Pull: Query Data from Deep Data Structures
[![CI](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml/badge.svg)](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml)
[![Code Coverage](https://codecov.io/gh/flybot-sg/lasagna-pull/branch/master/graph/badge.svg)](https://codecov.io/gh/flybot-sg/lasagna-pull)
[![Clojars](https://img.shields.io/clojars/v/sg.flybot/lasagna-pull.svg)](https://clojars.org/sg.flybot/lasagna-pull)
[![CljDoc](https://cljdoc.org/badge/sg.flybot/lasagna-pull)](https://cljdoc.org/d/sg.flybot/lasagna-pull)

## The Problem

Let's see some data copied from `deps.edn`:
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

In `clojure.core` we have `get` and `get-in` to extract information from a map, and we are very familiar with them. However, if we need to extract multiple pieces of information from different locations of the map, things are starting to get tricky.
```clojure
(let [global-path     (get-in data [:deps :paths])
      dev-extra-path  (get-in data [:deps :aliases :dev :extra-paths])
      test-extra-path (get-in data [:deps :aliases :test :extra-paths])]
  (concat global-path dev-extra-path test-extra-path)) ;=> ("src" "dev" "test")
```

We have to call `get`/`get-in` multiple times manually.
```clojure
((qfn '{:deps {:paths   ?global
               :aliases {:dev  {:extra-paths ?dev}
                         :test {:extra-paths ?test}}}}
      (concat ?global ?dev ?test))
 data) ;=> ("src" "dev" "test")
```

Pulling data from a sequence of maps using a Lasagna pattern returns a query function, which matches data using the pattern. A lasagna pattern mimics your data; you specify the pieces of information that you are interested in by using logic variables, which are just symbols starting with a `?`.

:bulb: `qfn` scans your pattern, binding them, then you can use them directly in the function's body. 

Another frequent situation is selecting a subset of data. We can use `select-keys` to shrink a map, and `map` it over a sequence of maps. Lasagna-pull provides this with an implicit binding `&?`:
```clojure
((qfn '{:deps {:paths ? :aliases {:dev {:extra-paths ?}}}} &?)
 data) ;=> {:deps {:paths ["src"], :aliases {:dev {:extra-paths ["dev"]}}}}
```

The unnamed binding `?` in the pattern is a placeholder; it will appear in `&?`.
```clojure
(def person&fruits {:persons [{:name "Alan", :age 20, :sex :male}
                              {:name "Susan", :age 12, :sex :female}]
                    :fruits [{:name "Apple", :in-stock 10}
                             {:name "Orange", :in-stock 0}]
                    :likes [{:person-name "Alan" :fruit-name "Apple"}]}) ;=> #'introduction/person&fruits
```

The pattern to select within the sequence of maps resembles the data itself:
```clojure
((qfn '{:persons [{:name ?}]} &?)
 person&fruits) ;=> {:persons [{:name "Alan"} {:name "Susan"}]}
```

By appending a logical variable to the sequence marking vector, we can capture a sequence of mappings.
```clojure
((qfn '{:persons [{:name ?} ?names]} (map :name ?names))
 person&fruits) ;=> ("Alan" "Susan")
```

### Filtering a Value

Sometimes, we need to filter a map on some keys. It can feel very intuitive to specify it in our pattern; let's find Alan's age.
```clojure
((qfn '{:persons [{:name "Alan" :age ?age}]} ?age)
 person&fruits) ;=> nil
```

The success of the above query is due to the fact that we filtered on `:name`, and there was only one item in `:persons` with the value of "Alan".
```clojure
(def q2 (qfn '{:a ?x :b {:c ?c :d ?x}} [?c ?x])) ;=> #'introduction/q2
(q2 {:a 1 :b {:c 5 :d 1}}) ;=> [5 1]
(q2 {:a 1 :b {:c 5 :d 2}}) ;=> [nil nil]
```

The second query above failed because `?x` was bound twice and they do not agree with each other.

## Pattern Options

In addition to basic queries, Lasagna-pull supports pattern options, which are decorators on the key part of a pattern, allowing you to fine-tune the query. An option is a pair, a keyword followed by its arguments.

### General Options

These options do not require a value of a certain type.

#### `:not-found` Option

The easiest option is `:not-found`, which provides a default value if a value is not found.
```clojure
((qfn '{(:a :not-found 0) ?a} ?a)
 {:b 3}) ;=> 0
```

#### `:when` Option

`:when` option has a `predict` function as its argument; a match only succeeds when this `predict` is fulfilled by the value.
```clojure
(def q (qfn {(list :a :when odd?) '?a} ?a)) ;=> #'introduction/q
(q {:a 3 :b 3}) ;=> 3
```

This will fail to match, e.g. return `nil`:
```clojure
(q {:a 2 :b 3}) ;=> nil
```

Note that the basic filter can also be thought of as a `:when` option. You can combine options together; they will be applied in their order.
```clojure
((qfn {(list :a :when even? :not-found 0) '?} &?)
 {:a -1 :b 3}) ;=> {:a 0}
```

#### Corrected

### The `:seq` option of the pattern options requires values to be a specific type

If you are about to match a large sequence, you might want to use pagination. This is essential if your values are lazy (sequential).
```clojure
((qfn '{(:a :seq [5 10]) ?} &?)
 {:a (range 1000)}) ;=> {:a (5 6 7 8 9 10 11 12 13 14)}
```

The design of putting options in the key parts of the patterns enables us to do nested queries.
```clojure
(def range-data {:a (map (fn [i] {:b i}) (range 100))}) ;=> #'introduction/range-data
((qfn '{(:a :seq [90 5]) [{:b ?} ?bs]} (map :b ?bs))
 range-data) ;=> (90 91 92 93 94)
```

#### You may store a function as a value in your map and, when querying it, you can apply arguments to it. The `:with` and `:batch` options enable you to do this.
```clojure
(defn square [x] (* x x)) ;=> #'introduction/square
((qfn '{(:a :with [5]) ?a} ?a)
 {:a square}) ;=> 25
```

And `:batch` will be applied multiple times, as if it were a sequence:
```clojure
((qfn {(list :a :batch (mapv vector (range 100)) :seq [40 5]) '?a} ?a)
 {:a square}) ;=> (1600 1681 1764 1849 1936)
```

These options are not just for convenience; if the embedded functions invoked by a query have side effects, these options could do [GraphQL's mutation](https://graphql.org/learn/queries/#mutations).
```clojure
(def a (atom 0)) ;=> #'introduction/a
((qfn {(list :a :with [5]) '?a} ?a)
 {:a (fn [x] (swap! a + x))}) ;=> 5
```

And the atom has been changed.
```clojure
@a ;=> 5
```

## Malli Schema Support
Lasagna-pull has optional support for [malli](https://github.com/metosin/malli) schemas. In Clojure, by including malli in your dependencies, it automatically checks your data pattern when you make queries to ensure that the syntax is correct.

Your query patterns not only need to follow the rules of the pattern, but also should conform to your data schema. This is especially important when you want to expose your data to the external world.

The `with-data-schema` macro lets you include your customized data schema (which applies to your data), then Lasagna-pull does a deeper check for the pattern in the body. It tries to find if the query pattern conforms to the meaning of the data schema (in terms of each value and your data structure).
```clojure
(defmacro try! [& body] `(try ~@body (catch Throwable e# #:error{:msg (ex-message e#)}))) ;=> #'introduction/try!
(def my-data-schema [:map [:a :int] [:b :keyword]]) ;=> #'introduction/my-data-schema
(try!
 (pull/with-data-schema my-data-schema
   (qfn '{:c ?c} ?c))) ;throws=>#:error{:type clojure.lang.Compiler$CompilerException, :message "Syntax error compiling at (0:0).", :data #:clojure.error{:phase :compile-syntax-check, :line 0, :column 0, :source "NO_SOURCE_PATH"}}
```
The **above** throws an exception! Because, once you have specified a data schema, you only allow users to query values documented in the schema (i.e., the closeness of a map). Since `:c` is not included in the schema, querying `:c` is invalid.
```clojure
(try!
 (pull/with-data-schema my-data-schema
   (qfn '{(:a :not-found "3") ?} &?))) ;throws=>#:error{:type clojure.lang.Compiler$CompilerException, :message "Syntax error compiling at (0:0).", :data #:clojure.error{:phase :compile-syntax-check, :line 0, :column 0, :source "NO_SOURCE_PATH"}}
```

The above code also triggers an exception, because `:a` is an `:int` as per the data schema, while you provided a `:not-found` pattern option value which is not an integer.

## Real world usage and some history

In Flybot, we use [fun-map](https://github.com/robertluo/fun-map) extensively. In a typical server backend, we put everything mutable into a single map called `system`, including atoms, databases, and even the web server itself. The fun-map library manages the lifecycles of these components and injects dependencies among them.

Then, we add database queries into `system`. They rely on databases, so the database value (we use Datomic) or connection (if using SQL databases) will be injected by fun-maps `fnk`, and while developing, we can easily replace the DB connection by mocked values.

Most queries also require input from user requests, so in the ring handler (of course, in `system`), we `merge` the HTTP request to `system`. This merged version then will be used as the source of all possible data.

The next problem is how to let users fetch the data. We weren't satisfied with the conventional REST style; GraphQL is good, but it adds too many unnecessary middle layers in our opinion. For example, we need to write many resolvers in order to glue it to the backend data. 

The first attempt made was [juxt pull](https://github.com/juxt/pull); I contributed to the 0.2 version and used it in some projects. This allowed users to construct a data pattern (the basic idea and syntax are similar to [EQL](https://github.com/edn-query-language/eql)) to pull data from the request-enriched `system`.

However, the syntax of pull does not go very well with deep and complex data structures (because the database information is also translated into fields). It just returns a trimmed version of the original map; users often need to walk the returned value again to find the pieces of information by themselves.

Lasagna-pull introduces a new query pattern to address these problems.

## Development

- Run `clojure -T:build ci`.
- This project uses a lot of [rct tests](https://github.com/robertluo/rich-comment-tests).

## About this README

This file is generated by `notebook/introduction.clj` using [clerk-doc](https://github.com/robertluo/clerk-doc).

## License

Copyright © 2022 Flybot Pte. Ltd. Apache License 2.0, http://www.apache.org/licenses/

```clojure
(ns introduction 
  (:require [sg.flybot.pullable :as pull :refer [qfn]]))
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
               'com.bhauman/figwheel-main {:mvn/version "0.2.18"},
               }},
             :test
             {:extra-paths ["test"],
              :extra-deps
              {'lambdaisland/kaocha {:mvn/version "1.80.1274"}},
              :main-opts ["-m" "kaocha.runner"]}}}});=> #'introduction/data

```
 In `clojure.core`, we have `get`, `get-in` to extract information 
 from a map and we are very familiar with them. However, if we have multiple 
 pieces of information need and they are stored in different location of the map, 
 things are starting getting tricky. 

```clojure
(let [global-path     (get-in data [:deps :paths])
      dev-extra-path  (get-in data [:deps :aliases :dev :extra-paths])
      test-extra-path (get-in data [:deps :aliases :test :extra-paths])]
  (concat global-path dev-extra-path test-extra-path));=> ("src" "dev" "test")

```
 We have to call `get`/`get-in` multiple times manually.
## Bring Lasagna-pull in
 Using lasagna-pull, we can do it in a more intuitive way:
```clojure
((qfn '{:deps {:paths   ?global
               :aliases {:dev  {:extra-paths ?dev}
                         :test {:extra-paths ?test}}}}
      (concat ?global ?dev ?test))
 data);=> ("src" "dev" "test")

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
((qfn '{:deps {:paths ? :aliases {:dev {:extra-paths ?}}}} &?) data);=> {:deps {:paths ["src"], :aliases {:dev {:extra-paths ["dev"]}}}}

```
 The unnamed binding `?` in the pattern is a placeholder, it will appear
 in `&?`.
### Sequence of maps
 It is very common that our data include maps in a sequence, like this:
```clojure
(def person&fruits {:persons [{:name "Alan", :age 20, :sex :male}
                              {:name "Susan", :age 12, :sex :female}]
                    :fruits [{:name "Apple", :in-stock 10}
                             {:name "Orange", :in-stock 0}]
                    :likes [{:person-name "Alan" :fruit-name "Apple"}]});=> #'introduction/person&fruits

```
 The pattern to select inside the sequence of maps just look like the data itself:
```clojure
((qfn '{:persons [{:name ?}]} &?) person&fruits);=> {:persons [{:name "Alan"} {:name "Susan"}]}

```
 by append a logical variable in the sequence marking vector, we can capture
 a sequence of map.
```clojure
((qfn '{:persons [{:name ?} ?names]} (map :name ?names)) person&fruits);=> ("Alan" "Susan")

```
### Filter a value

 Sometimes, we need filtering a map on some keys. It is very intuitive to specific
 it in your pattern, let's find alan's age:
```clojure
((qfn '{:persons [{:name "Alan" :age ?age}]} ?age) person&fruits)
```
### Using same named lvar multiple times to join

 If a named lvar bound for more than one time, its value has to be the same, otherwise
 all matching fails. We can use this to achieve a join, let's find Alan's favorite fruit
 in-stock value:
```clojure
((qfn '{:persons [{:name "Alan"}]
        :fruits  [{:name ?fruit-name :in-stock ?in-stock}]
        :likes   [{:person-name "Alan" :fruit-name ?fruit-name}]}
      ?in-stock)
 person&fruits);=> 10

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
((qfn '{(:a :not-found 0) ?a} ?a) {:b 3});=> 0

```
 #### `:when` option

 `:when` option has a predict function as it argument, a match only succeed when this
 predict fulfilled by the value.
```clojure
(def q (qfn {(list :a :when odd?) '?a} ?a));=> #'introduction/q

(q {:a 3 :b 3});=> 3

```
 This will failed to match, e.g. returns nil:
```clojure
(q {:a 2 :b 3})
```
 > Note that the basic filter can also be thought as a `:when` option.

 You can combine options together, they will be applied by their order:
```clojure
((qfn {(list :a :when even? :not-found 0) '?} &?) {:a -1 :b 3});=> {:a 0}

```
 ### Pattern options requires values be a specific type

 #### `:seq` option
 
 If you are about to match a big sequence, you might want to do pagination. It is essential
 if you values are lazy.
```clojure
((qfn '{(:a :seq [5 5]) ?} &?) {:a (range 1000)});=> {:a (5 6 7 8 9)}

```
 The design of putting options in the key part of the patterns, enable us
 to do nested query.
```clojure
(def range-data {:a (map (fn [i] {:b i}) (range 100))});=> #'introduction/range-data

((qfn '{(:a :seq [5 5]) [{:b ?} ?bs]} (map :b ?bs)) range-data);=> (5 6 7 8 9)

```
 #### `:with` and `:batch` options

 You may store a function as a value in your map, then when querying it, you
 can apply arguments to it, `:with` enable you to do it:
```clojure
(defn square [x] (* x x));=> #'introduction/square

((qfn '{(:a :with [5]) ?a} ?a) {:a square});=> 25

```
 And `:batch` will apply many times on it, as if it is a sequence:
```clojure
((qfn {(list :a :batch (mapv vector (range 100)) :seq [5 5]) '?a} ?a)
 {:a square});=> (25 36 49 64 81)

```
 These options not just for conviniece, if the embeded functions invoked by 
 a query has side effects, these options could do 
 [GraphQL's mutation](https://graphql.org/learn/queries/#mutations).
```clojure
(def a (atom 0));=> #'introduction/a

((qfn {(list :a :with [5]) '?a} ?a)
 {:a (fn [x] (swap! a + x))});=> 5

```
 And the atom has been changed:
```clojure
@a;=> 5

```

 ## License
 Copyright. © 2022 Flybot Pte. Ltd.
 Apache License 2.0, http://www.apache.org/licenses/

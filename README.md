# Pullable, precisely select from deep data structure.
![CI](https://github.com/robertluo/pullable/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/robertluo/pullable/branch/master/graph/badge.svg)](https://codecov.io/gh/robertluo/pullable)

## Rational

Organizing all data into a big data structure makes it a single point of truth, is a good practice for Clojure app. 

 - Data is organized by its domain nature, easy to navigate.
 - No more duplicated data, prevents all kinds of out-of-sync problems.
 - By checking this app data, program can be easier to reason about.

If you use [fun-map](https://github.com/robertluo/fun-map), you can even put all your database and its operations into this big map, making this approach easier for real world, complicate data.

Clojure has built-in functions like `select-keys` that can return the same shape data from a map, but it is too limited.

Inspired by [Datomic Pull API](https://docs.datomic.com/on-prem/pull.html) and [EQL](https://edn-query-language.org/eql/1.0.0/what-is-eql.html), this simple library provide you with a simple and precise pattern that allow you to pull data out in one call.

The idea of a general-purpose query language for native Clojure data structure should have features like:

 - Can be used locally or remotely, when using it remotely as an app API is just a natural extension.
 - It makes no sense that remote app can have some feature while code in same app can not.
 - Can be expressed in pure clojure data structure.
 - Follows the shape of app data, users can query by an example.
## Query pattern

Generally, query patterns just have same structure of your data, where there is a map, you use map in place of it; where there is a sequence (vector, list, etc.) of maps, you use a vector in place.

You mark the data you are interested by a special `'?`, so:

```clojure
(pull/run '{:a ?,:b ?} {:a 1, :b 2, c: 3}) => [{:a 1, :b 2} {}]
```
This works just like `select-keys`, only query patterns look like an example; but why is the returned value a pair of maps here?

Because query patterns also support logical variable which is a symbol starting with `?`, so `run` returns the matched data and a logical variable map, let's try:

```clojure
(pull/run '{:a ?a, :b ?b} {:a 1, :b 2, :c 3}) => [{:a 1, :b 2} {'?a 1 '?b 2}]
```

You can expect that scalar value works like a filter, causing matching fail:

```clojure
(pull/run '{:a ?, :b 2} {:a 1, :b 1}) => [{} {}] ;; value of :b does not match pattern
```

and if same logical variable can not contain a single value, then all of them fail:

```clojure
(pull/run '{:a ?x, :b ?x} {:a 2, :b 1}) => [{} {}]
```

Of course pattern support nested maps:

```clojure
(pull/run '{:a {:b ?b}} {:a {:b 3}}) => [{:a {:b 3}} {'?b 3}]
```

### special cases

To match a sequence of maps, using `[]` to surround it:

```clojure
(pull/run '[{:a ?}] [{:a 3, :b 4} {:a 1} {}]) => [[{:a 3} {:a 1} {}], {}]
```

Put logical variable after this single inner map, binding it to the whole sequence:

```clojure
(pull/run '[{:a ?} ?x] [{:a 3, :b 4} {:a 1} {}]) => [[{:a 3} {:a 1} {}], {'?x [{:a 3} {:a 1} {}]}]
```

### Query post processors

After a query matches to data, we can pass some options to it, using a list to specify them:

#### `not-found`

```clojure
(pull/run '{:a (? :not-found ::not-found)} {:b 5}) => [{:a ::not-found} {}]
```

#### `when`

```clojure
(pull/run {:a (list '? :when even?)} {:a 5}) => [{} {}] ;;not found because the value is not even
```
#### `with`

If the value of a query is a function, using `:with` option can invoke it and returns the result instead:

```clojure
(pull/run '{:a (? :with [5])} {:a #(* % 2)}) => [{:a 10} {}]
```
#### `batch`

Batched version of `:with` option:

```clojure
(pull/run '{:a (? :batch [[5] [7]])} {:a #(* % 2)}) => [{:a [10 14]} {}]
```

#### `seq`

Apply to sequence value of a query, useful for pagination:

```clojure
(pull/run '[{:a (? :seq [1 3])}]} [{:a 0} {:a 1} {:a 2} {:a 3} {:a 4}]) => [[{:a 1} {:a 2} {:a 3}] {}]
```

## License
Copyright © 2020 Robertluo

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

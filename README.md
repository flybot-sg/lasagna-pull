# Pullable, precisely select from deep data structure.
![CI](https://github.com/flybot-sg/lasagna-pull/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/flybot-sg/lasagna-pull/branch/master/graph/badge.svg)](https://codecov.io/gh/flybot-sg/lasagna-pull)
[![Clojars Project](https://img.shields.io/clojars/v/sg.flybot/lasagna-pull.svg)](https://clojars.org/sg.flybot/lasagna-pull)
[![Cljdoc](https://cljdoc.org/badge/sg.flybot/lasagna-pull)](https://cljdoc.org/d/sg.flybot/lasagna-pull)

## Rational

Maps and collections are butter and bread in Clojure, and we store all kinds of data. Therefore, getting data (aka query) is very important, and `get-in` and `select-keys` are very common in our code.

However, if maps are deeply nested, this approach becomes cumbersome. Some excellent libraries are trying to make it easy, like [spector](https://github.com/redplanetlabs/specter) and [Datomic pull API](https://docs.datomic.com/on-prem/query/pull.html).

`Lasagna-pull`'s `query` turns a query pattern (a kind of data structure) into a function:

```clojure
(require '[sg.flybot.pullable :as pull])

(def my-query (pull/query '{:a ? :b {:c ?}}))

(my-query {:a 3 :d 5 :b {:e {:f :something} :c "Hello"}}) ;=> [{:a 3 :b {:c "Hello"}} nil]
```

The design purposes of the pulling pattern are:

 - Pure Clojure data structure.
 - Similar to the data structure which you are going to query. You could think of it as an example.

## Core Functions

 - `query`: Returns a query function that can query data.
 ~~- `run`: Runs a query directly.~~

 Run query against data return a pair of resulting data and named bindings. 

 - If there are no matches, the resulting data will be `nil`
 - If no named bindings are specified in the pattern, that part will be `nil`.

## Data error

If running a pattern for given `data` encounters unexpected data, it will not throw exceptions. A particular error data will be generated in place instead. 

```clojure
(run '{:a ?} 3) ;=> {:a #error{...}}
```

## Query pattern

Generally, query patterns have the same structure as your data; where there is a map, you use a map in place of it; where there is a sequence (vector, list, etc.) of maps, you use a vector in place.

You mark the data you are interested in by a special `'?`, so:

```clojure
(pull/run '{:a ?,:b ?} {:a 1, :b 2, c: 3}) => [{:a 1, :b 2} {}]
```

This works just like `select-keys` only query patterns look like an example, but why is the returned value a pair of maps here?

Because query patterns also support logical variable, which is a symbol starting with `?`, so `run` returns the matched data and a logical variable map, let's try:

```clojure
(pull/run '{:a ?a, :b ?b} {:a 1, :b 2, :c 3}) => [{:a 1, :b 2} {'?a 1 '?b 2}]
```

You can expect that scalar value works as a filter, causing matching to fail:

```clojure
(pull/run '{:a ?, :b 2} {:a 1, :b 1}) => [{} {}] ;; value of :b does not match pattern
```

and if the same logical variable can not contain a single value, then all of them fail:

```clojure
(pull/run '{:a ?x, :b ?x} {:a 2, :b 1}) => [{} {}]
```

Of course, pattern support nested maps:

```clojure
(pull/run '{:a {:b ?b}} {:a {:b 3}}) => [{:a {:b 3}} {'?b 3}]
```

### sequence of maps

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
(pull/run '{(:a :not-found ::not-found) ?} {:b 5}) => [{:a ::not-found} {}]
```

#### `when`

```clojure
(pull/run {(:a :when even?) '?} {:a 5}) => [{} {}] ;;not found because the value is not even
```
#### `with`

If the value of a query is a function, using `:with` option can invoke it and returns the result instead:

```clojure
(pull/run '{(:a :with [5]) ?} {:a #(* % 2)}) => [{:a 10} {}]
```
#### `batch`

Batched version of `:with` option:

```clojure
(pull/run '{(:a :batch [[5] [7]]) ?} {:a #(* % 2)}) => [{:a [10 14]} {}]
```

#### `seq`

Apply to sequence value of a query, useful for pagination:

```clojure
(pull/run '[{:a ? :b ?} ? :seq [2 3]] [{:a 0} {:a 1} {:a 2} {:a 3} {:a 4}]) => [[{:a 1} {:a 2} {:a 3}] {}]
```

## License
Copyright. © 2022 Flybot Pte. Ltd.
Apache License 2.0, http://www.apache.org/licenses/

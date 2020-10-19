# Pullable, precisely select from deep data structure.
![CI](https://github.com/robertluo/pullable/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/robertluo/pullable/branch/master/graph/badge.svg)](https://codecov.io/gh/robertluo/pullable)

## Rational

Data for an application can be big, we always need a way to allow user to select derived data from a recursive data structure. 

Clojure has built-in function like `select-keys` can return same shape data from a big map, but it is too limited.

Inspired by [Datomic Pull API](https://docs.datomic.com/on-prem/pull.html) and [EQL](https://edn-query-language.org/eql/1.0.0/what-is-eql.html), this simple library provide you a simple and precise pattern allow you to pull data out in one call.

## Pattern

A pattern is a recursive data structure specific your desired data:

### Simple Pattern

Any value, just like clojure's `get` function, but returns the same data shape:

```clojure
(pull {:a 3 :b 4} :a) ;=> {:a 3}
```

When pulling on a sequence of data, it will apply to every element:

```clojure
(pull [{:a 5 :b 3} {:a 3 :b 2}] :a) ;=> [{:a 5} {:a 3}]

;same structure means if you are pulling from a set, it will also return a set
(pull #{{:a 5 :b 3} {:a 3 :b 2}} :a) ;=> #{{:a 5} {:a 2}}

```

### Vector pattern

Putting patterns inside a vector makes vector pattern, it results doing those queries one by one, and returns same structure.

```clojure
(pull {:a 3 :b 4 :c 5} [:a :b]) ;=> like select-keys, returns {:a 3 :b 4} 
(pull [{:a 3 :b 4 :c 5} {:a 5}] ;=> [{:a 3 :b 4} {:a 5 :b :robertluo.pull.core/::noe}]
```

### Join pattern

A join pattern is map which keys are patterns, and the values also are patterns. It results a map with the keys are the keys of key patterns, and values are value patterns.

```clojure
(pull {:a {:b 4 :c 5}} {:a :b}) ;=> {:a {:b 4}}
(pull {:a {:b 4 :c 5 :d 6}} {:a [:b :d]} ;=> {:a {:b 4 :d 6}}
```

### Pattern options

You can pass options to any pattern by using a list, with the first element is the pattern itself, and options are pairs of a keyword (option name) and its argument.

#### `:seq` option

If a value is a collection of maps need to pull, you can specify by add
`:seq` option to a pattern (pagination).

```clojure
(pull [{:a 0} {:a 1} {:a 2} {:a 3} {:a 4}] '(:a :seq [1 2])) ;=> [{:a 1} {:a 2}] 
```

#### `:not-found` option

If a value is not present in data, its value pulled will be `:robertluo.pullable.core/not-found` by default. However, you can replace it by using `:not-found 0` option.

```clojure
(pull {} '(:a :not-found 0)) ;=> [{:a 0}]
```

#### `:with` option

If a value is a function, you can pass `:with` arguments, it will apply these arguments to the function and return it.

```clojure
(pull {:a inc} '(:a :with [2])) ;=> {:a 3}
```

## License
Copyright © 2020 Robertluo

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

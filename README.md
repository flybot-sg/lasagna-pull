# Pullable, precisely select from deep data structure.
![CI](https://github.com/robertluo/pullable/workflows/CI/badge.svg)
[![codecov](https://codecov.io/gh/robertluo/pullable/branch/master/graph/badge.svg)](https://codecov.io/gh/robertluo/pullable)

## Rational

Data for an application can be big, we always need a way to allow user to select derived data from a recursive data structure. 

Clojure has built-in function like `select-keys` can return same shape data from a big map, but it is too limited.

Inspired by [Datomic Pull API](https://docs.datomic.com/on-prem/pull.html) and [EQL](https://edn-query-language.org/eql/1.0.0/what-is-eql.html), this simple library provide you a simple and precise pattern allow you to pull data out in one call.

## Usage

One single function `pull` let you describe a data pattern, specific how you want to pull from a source data structure.

```clojure
(require '[robertluo.pullable :refer [pull]])
(pull {:a 3 :b 4} :a)
```

## Pattern

A pattern is a data structure specific your desired data:

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

;it will 
```

### Vector pattern

By grouping pattern together, a vector pattern let you 

Pattern specification:
  - key is just a normal value, ofter is a keyword.
  - vector of keys.
  - map is a join, means go one layer deeper into data.
  - list can apply options to a pulling.
 
For example:
  pattern `[:a :b]` means pull `:a`, `:b` from data, same result as `select-keys`.
  where `[:a {:b [:c]}]` means get `:a` and `(get-in [:b :c])`.

### `:depth` option

If data is recursive, like `{:child {:age 15 :child {:age 20 :child {:age 3}}}}`, you do not need to write it all by hand, specific `{:child [:age] :depth 1}` will expand to `{:child [:age {:child [:age]}]}` automatically. 

### `:seq` option

If a value is a collection of maps need to pull, you can specify by add
`:seq` option to a pattern.
For example:
  `[:a {:b [:c] :seq []}]` or `(:a :seq [])`

Notice that you must explicit specific it, this is different from Datomic Pull.
`:seq` option also can specific offset and limit. e.g. `:seq [10 20]` means pulling
from the collection starting from index 10 (0 based), and take 20.

### `:not-found` option

If a value is not present in data, its value pulled will be `:robertluo.pullable.core/not-found` by default. However, you can specific it by using `:not-found 0` to change it.

### `:with` option

If a value is a function, you can pass `:with` arguments, it will apply these arguments to the function and return it.

### `:batch` option

If you want to call `:with` multiple times, `:batch` option will return a sequence of calling returning value also in a sequence.

## Error Handling

When an exception raised when pulling a key, the corresponding value will be an error map like:

`{:error/key :key-of-error, :error/message "the exception message", :error/data {...}}`

You can provide an global exception handler function which will receive this error map as the argument, and the return value will be the value instead. A common practice might be logging.


## License
Copyright © 2020 Robertluo

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.

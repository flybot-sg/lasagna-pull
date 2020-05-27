# Pullable, precisely select from deep data structure.
[![Actions Status](https://github.com/robertluo/pullable/workflows/build/badge.svg)](https://github.com/robertluo/pullable/actions)

## Rational

Data for an application can be big, we always need a way to allow user to select derived data from a recursive data structure. 

Clojure has built-in function like `select-keys` can return same shape data from a big map, but it is too limited.

Inspired by [Datomic Pull API](https://docs.datomic.com/on-prem/pull.html) and [EQL](https://edn-query-language.org/eql/1.0.0/what-is-eql.html), this simple library provide you a simple and precise pattern allow you to pull data out in one call.

## Pattern

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

## License
Copyright © 2020 Robertluo

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
(ns robertluo.pullable
  (:require
   [robertluo.pullable.core :as impl]))

(defn pull
  "Returns pulling result from data using pattern ptn.
   Like `clojure.core/select-keys`, but more powerful.
   Result data has the same structure of data.
   Data can be a map, or collection of maps, potentially very big.
   Inspired by [Datomic Pull](https://docs.datomic.com/on-prem/pull.html).
   
   Pattern specification:
    - key is just a normal value, ofter is a keyword.
    - vector of keys.
    - map is a join, means go one layer deeper into data.
    - list can apply options to a pulling.
   
   For example:
     pattern `[:a :b]` means pull `:a`, `:b` from data, same result as `select-keys`.
     where `[:a {:b [:c]}]` means get `:a` and `(get-in [:b :c])`.
   
   If a value is a collection of maps need to pull, you can specify by add
   :seq option to a pattern.
  
   For example:
     `[:a {:b [:c] :seq []}]` or `(:a :seq [])`
   
   Notice that you must explicit specific it, this is different from Datomic Pull.
   `:seq` option also can specific offset and limit. e.g. `:seq [10 20]` means pulling
   from the collection starting from index 10 (0 based), and take 20.
   "
  [data ptn]
  (when-let [q (impl/pattern->query ptn)]
    (impl/-select q data)))
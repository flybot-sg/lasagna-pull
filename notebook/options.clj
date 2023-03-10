;;# Introduction to options
(ns options
  (:require [sg.flybot.pullable :as pull]))
;; In addition to basic query, Lasagna-pull support pattern options, it is a decorator
;; on the key part of a pattern, let you fine tune the query.
;; An option is a pair, a keyword followed by its arguments.

;;## General options
;; These options does not require a value in a specific type.

;;### `:not-found` option
;; The easiest option is `:not-found`, it provides a default value if a value is not
;; found.

(-> (pull/run-query '{(:a :not-found 0) ?a} {:b 3}) (pull/lvar-val '?a))

;;### `:when` option
;; `:when` option has a predict function as it argument, a match only succeed when this
;; predict fulfilled by the value.

(def q (pull/query {(list :a :when odd?) '?a}))
(-> (q {:a 3 :b 3}) (pull/lvar-val '?a))

;; This will failed to match:

(-> (q {:a 2 :b 3}) (pull/lvar-val '?a))

;; Note that the basic filter can also be thought as a `:when` option.

;; You can combine options together, they will be applied by their order:
(pull/run-query {(list :a :when even? :not-found 0) '?} {:a -1 :b 3})

;; ## Options requires values be a specific type

;; ### `:seq` option
;; If you are about to match a big sequence, you might want to do pagination. It is essential
;; if you values are lazy.

(-> (pull/run-query '{(:a :seq [5 5]) ?} {:a (range 1000)}))

;; The design of putting options in the key part of the patterns, enable us
;; to do nested query.

(def data {:a (map (fn [i] {:b i}) (range 100))})
(-> (pull/run-query '{(:a :seq [5 5]) [{:b ?} ?b]} data) (pull/lvar-val '?b))

;;### `:with` and `:batch` options
;; You may store a function as a value in your map, then when querying it, you
;; can apply arguments to it, `:with` enable you to do it:
(defn square [x] (* x x))
(-> (pull/run-query '{(:a :with [5]) ?a} {:a square}) (pull/lvar-val '?a))

;; And `:batch` will apply many times on it, as if it is a sequence:
(-> (pull/run-query {(list :a :batch (mapv vector (range 100)) :seq [5 5]) '?a}
                    {:a square})
    (pull/lvar-val '?a))

;; These options not just for conviniece, if the embeded functions invoked by 
;; a query has side effects, these options could do 
;; [GraphQL's mutation](https://graphql.org/learn/queries/#mutations).
(def a (atom 0))
(-> (pull/run-query {(list :a :with [5]) '?a} {:a (fn [x] (swap! a + x))})
    (pull/lvar-val '?a))

;; And the atom has been changed:
@a
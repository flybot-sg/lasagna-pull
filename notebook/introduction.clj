
(ns introduction 
  (:require [sg.flybot.pullable :as pull]))

;;# Lasagna-pull, query data from deep data structure
;;[![CI](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml/badge.svg)](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml)
;;[![Code Coverage](https://codecov.io/gh/flybot-sg/lasagna-pull/branch/master/graph/badge.svg)](https://codecov.io/gh/flybot-sg/lasagna-pull)
;;[![Clojars](https://img.shields.io/clojars/v/sg.flybot/lasagna-pull.svg)](https://clojars.org/sg.flybot/lasagna-pull)
;;[![CljDoc](https://cljdoc.org/badge/sg.flybot/lasagna-pull)](https://cljdoc.org/d/sg.flybot/lasagna-pull)

;;## Problem

;; Let's see some data copied from deps.edn:

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
              :main-opts ["-m" "kaocha.runner"]}}}})

;; In `clojure.core`, we have `get`, `get-in` to extract information 
;; from a map and we are very familiar with them. However, if we have multiple 
;; pieces of information need and they are stored in different location of the map, 
;; things are starting getting tricky. 
;;

(let [global-path     (get-in data [:deps :paths])
      dev-extra-path  (get-in data [:deps :aliases :dev :extra-paths])
      test-extra-path (get-in data [:deps :aliases :test :extra-paths])]
  (concat global-path dev-extra-path test-extra-path))

;; We have to call `get`/`get-in` multiple times manually.

;;## Bring Lasagna-pull in

;; Using lasagna-pull, we can do it in a more intuitive way:

(def pattern '{:deps {:paths   ?global
                      :aliases {:dev  {:extra-paths ?dev}
                                :test {:extra-paths ?test}}}})

;; As you may see above, lasagna-pull using a pattern which mimics your data
;; to match it, a logic variable marks the piece of information we are interested,
;; it is easy to write and easy to understand.

(let [[_ {:syms [?global ?dev ?test]}] (pull/run-query pattern data)]
     (concat ?global ?dev ?test))

;; `pull/match` takes a pattern and match it to data, returns a pair, and
;; the second item of the pair is a map, contains all logical variable (a.k.a lvar)
;; bindings.

;;### Select subset of your data

;; Another frequent situation is selecting subset of data, we could use 
;; `select-keys` to shrink a map, and `map` it over a sequence of maps.
;; Lasagna-pull provide this with nothing to add:

(-> (pull/run-query pattern data) first)

;; Just check the first item of the matching result, it only contains
;; information we asked, retaining the original data shape.

;;### Sequence of maps
;; It is very common that our data include maps in a sequence, like this:

(def person&fruits {:persons [{:name "Alan", :age 20, :sex :male}
                              {:name "Susan", :age 12, :sex :female}]
                    :fruits [{:name "Apple", :in-stock 10}
                             {:name "Orange", :in-stock 0}]
                    :likes [{:person-name "Alan" :fruit-name "Apple"}]})

;; The pattern to select inside the sequence of maps just look like the data itself:

(pull/run-query '{:persons [{:name ?}]} person&fruits)

;; Logical variable `?` is unnamed, it means it will included in the query result,
;; but not in the resolved binding map. 

(-> (pull/run-query '{:persons [{:name ? :age ?} ?names]} person&fruits) (pull/lvar-val '?names))

;; by append a logical variable in the sequence marking vector, we can capture
;; a sequence of map.

;;### Filter a value
;; Sometimes, we need filtering a map on some keys. It is very intuitive to specific
;; it in your pattern, let's find alan's age:

(-> (pull/run-query '{:persons [{:name "Alan" :age ?age}]} person&fruits) (pull/lvar-val '?age))

;;### Using same named lvar multiple times to join
;; If a named lvar bound for more than one time, its value has to be the same, otherwise
;; all matching fails. We can use this to achieve a join, let's find Alan's favorite fruit
;; in-stock value:

(-> (pull/run-query '{:persons [{:name "Alan"}]
                      :fruits  [{:name ?fruit-name :in-stock ?in-stock}]
                      :likes   [{:person-name "Alan" :fruit-name ?fruit-name}]}
                    person&fruits)
    (pull/lvar-val '?in-stock))

;;> There is a macro for you to define a *query function* just like `fn`, but takes
;;> a pattern to match data. 

(def find-add (pull/qn [?x ?y] '{:x ?x :y ?y} (+ ?x ?y)))

;; This function accept data and do the rest just like `fn`
 
(find-add {:x 5 :y 15})

;; ## Query Options

;; In addition to basic query, Lasagna-pull support pattern options, it is a decorator
;; on the key part of a pattern, let you fine tune the query.
;; An option is a pair, a keyword followed by its arguments.
;;
;; ### General options
;;
;; These options does not require a value in a specific type.
;;
;; #### `:not-found` option
;;
;; The easiest option is `:not-found`, it provides a default value if a value is not
;; found.

(-> (pull/run-query '{(:a :not-found 0) ?a} {:b 3}) (pull/lvar-val '?a))

;; #### `:when` option
;;
;; `:when` option has a predict function as it argument, a match only succeed when this
;; predict fulfilled by the value.

(def q (pull/query {(list :a :when odd?) '?a}))
(-> (q {:a 3 :b 3}) (pull/lvar-val '?a))

;; This will failed to match:

(-> (q {:a 2 :b 3}) (pull/lvar-val '?a))

;; Note that the basic filter can also be thought as a `:when` option.

;; You can combine options together, they will be applied by their order:
(pull/run-query {(list :a :when even? :not-found 0) '?} {:a -1 :b 3})

;; ### Options requires values be a specific type

;; #### `:seq` option
;; 
;; If you are about to match a big sequence, you might want to do pagination. It is essential
;; if you values are lazy.

(-> (pull/run-query '{(:a :seq [5 5]) ?} {:a (range 1000)}))

;; The design of putting options in the key part of the patterns, enable us
;; to do nested query.

(def range-data {:a (map (fn [i] {:b i}) (range 100))})
(-> (pull/run-query '{(:a :seq [5 5]) [{:b ?} ?b]} range-data) (pull/lvar-val '?b))

;; #### `:with` and `:batch` options
;;
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

;;
;; ## License
;; Copyright. © 2022 Flybot Pte. Ltd.
;; Apache License 2.0, http://www.apache.org/licenses/


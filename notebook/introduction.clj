
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


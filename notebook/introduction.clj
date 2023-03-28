
(ns introduction
  (:require [sg.flybot.pullable :as pull :refer [qfn]]))

;;# Lasagna-Pull: Query Data from Deep Data Structures
;; 
;;[![CI](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml/badge.svg)](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml)
;;[![Code Coverage](https://codecov.io/gh/flybot-sg/lasagna-pull/branch/master/graph/badge.svg)](https://codecov.io/gh/flybot-sg/lasagna-pull)
;;[![Clojars](https://img.shields.io/clojars/v/sg.flybot/lasagna-pull.svg)](https://clojars.org/sg.flybot/lasagna-pull)
;;[![CljDoc](https://cljdoc.org/badge/sg.flybot/lasagna-pull)](https://cljdoc.org/d/sg.flybot/lasagna-pull)
;;
;;## The Problem
;;
;; Let's see some data copied from `deps.edn`:

(def data {:deps
           {:paths ["src"],
            :deps {},
            :aliases
            {:dev
             {:extra-paths ["dev"],
              :extra-deps
              {'metosin/malli {:mvn/version "0.10.2"},
               'com.bhauman/figwheel-main {:mvn/version "0.2.18"}}},
             :test
             {:extra-paths ["test"],
              :extra-deps
              {'lambdaisland/kaocha {:mvn/version "1.80.1274"}},
              :main-opts ["-m" "kaocha.runner"]}}}})
;;
;; In `clojure.core `we have `get `and `get-in `to extract information from a map,
;; and we are very familiar with them. However, if we need to extract multiple 
;; pieces of information from different locations of the map, things are starting 
;; to get tricky. We have to call `get`/`get-in` multiple times manually.
;;

(let [global-path     (get-in data [:deps :paths])
      dev-extra-path  (get-in data [:deps :aliases :dev :extra-paths])
      test-extra-path (get-in data [:deps :aliases :test :extra-paths])]
  (concat global-path dev-extra-path test-extra-path))

;; ## Lasagna-Pull way
;; 
;; Using lasagna-pull, we can do it in a more intuitive way,
;; `pull/qfn` returns a query function, which matches data using a Lasagna pattern.

((qfn '{:deps {:paths   ?global
               :aliases {:dev  {:extra-paths ?dev}
                         :test {:extra-paths ?test}}}}
      (concat ?global ?dev ?test))
 data)

;; A lasagna pattern mimics your data; you specify the pieces of information that
;; you are interested in by using logic variables, which are just symbols starting
;; with a `?`.
;; > :bulb: `qfn` scans your pattern, binding them, then you can use them directly
;; >in the function's body. 
;;
;; Another frequent situation is selecting a subset of data. We can use 
;; `select-keys` to shrink a map, and `map` it over a sequence of maps. Lasagna-pull
;; provides this with an implicit binding `&?`:

((qfn '{:deps {:paths ? :aliases {:dev {:extra-paths ?}}}} &?)
 data)

;; The unnamed binding `?` in the pattern is a placeholder, it will appear
;; in `&?`.
;; 
;; ### Sequence of maps
;;
;; The pattern to select inside the sequence of the maps just look like the data itself:

(def person&fruits {:person/name "Alan",
                    :person/age 20,
                    :person/sex :male
                    :fruits [{:name "Apple", :in-stock 10}
                             {:name "Orange", :in-stock 0}]
                    :likes  [{:person-name "Alan" :fruit-name "Apple"}]})

;; The pattern to select inside the sequence of maps just look like the data itself:

((qfn '{:fruits [{:name ?}]} &?)
 person&fruits)

;; By appending a logical variable to the sequence marking vector, we can capture
;; a sequence of maps.

((qfn '{:fruits [{:name ?} ?names]} (map :name ?names))
 person&fruits)

;; 
;; ### Filter a value
;;
;; Sometimes, we need to filter a map on some keys. It can feel very intuitive to
;; specify it in our pattern; let's find Alan's age.

((qfn '{:person/name "Alan", :person/age ?age} ?age)
 person&fruits)

;; The success of the above query is due to the fact that we filtered on `:person/name`.
;;
;; ### Using same named lvar multiple times to join
;;
;;  If a named lvar bound more than one time, its value has to be the same, otherwise
;;  all matching fails. We can use this to join:

(def q2 (qfn '{:a ?x :b {:c ?c :d ?x}} [?c ?x]))
(q2 {:a 1 :b {:c 5 :d 1}})
(q2 {:a 1 :b {:c 5 :d 2}})

;; 
;; The second query above failed because `?x` bound twice and they do not agree with
;; each other.
;;
;; ## Pattern Options
;; In addition to basic queries, Lasagna-pull supports pattern options, which are decorators 
;; on the key part of a pattern, allowing you to fine-tune the query. An option is a 
;; pair, a keyword followed by its arguments.
;;
;; ### General options
;;
;; These options do not require a value of a certain type.
;;
;; #### `:not-found` option
;;
;; The easiest option is `:not-found`, it provides a default value if a value is not
;; found.

((qfn '{(:a :not-found 0) ?a} ?a)
 {:b 3})

;; 
;; #### `:when` option
;;
;; `:when` option has a predict function as it argument, a match only succeed when this
;; predict fulfilled by the value.

(def q (qfn {(list :a :when odd?) '?a} ?a))
(q {:a 3 :b 3})

;; This will failed to match, e.g. returns `nil`:

(q {:a 2 :b 3})

;; > Note that the basic filter can also be thought as a `:when` option.
;;
;; You can combine options together, they will be applied by their order:
((qfn {(list :a :when even? :not-found 0) '?} &?)
 {:a -1 :b 3})

;; 
;; ### Pattern options requires a value be a certain type
;;
;; #### `:seq` option
;; 
;; If you are about to match a large sequence, you might want to use pagination. 
;; This is essential if your values are lazy (sequential) .

((qfn '{(:a :seq [5 10]) ?} &?)
 {:a (range 1000)})

;; The design of putting options in the key part of the patterns, enable us
;; to do nested queries.

(def range-data {:a (map (fn [i] {:b i}) (range 100))})
((qfn '{(:a :seq [90 5]) [{:b ?} ?bs]} (map :b ?bs))
 range-data)

;; #### `:with` and `:batch` options
;;
;; You may store a function as a value in your map, then when querying it, you
;; can apply arguments to it, `:with` enable you to do it:

(defn square [x] (* x x))
((qfn '{(:a :with [5]) ?a} ?a)
 {:a square})

;; And `:batch` will apply many times on it, as if it is a sequence:

((qfn {(list :a :batch (mapv vector (range 100)) :seq [40 5]) '?a} ?a)
 {:a square})

;;  These options not just for convinience, if the embeded functions invoked by 
;;  a query has side effects, these options could do 
;;  [GraphQL's mutation](https://graphql.org/learn/queries/#mutations).

(def a (atom 0))
((qfn {(list :a :with [5]) '?a} ?a)
 {:a (fn [x] (swap! a + x))})

;; And the atom has been changed:
@a

;;  ## Malli schema support
;; 
;;  Lasagna-pull has optional support for [malli](https://github.com/metosin/malli) schemas.
;;  In Clojure, by put malli in your dependencies, it automatically checks your data
;;  pattern when you make queries to ensure that the syntax is correct.
;;
;;  Your query patterns not only follow the rules of pattern, it also should conform 
;;  to your data schema. It is especially important when you want to expose your data to
;;  the external world.
;;
;;  The `with-data-schema` macro let you include your customized data schema (applies to your data),
;;  Lasagna-pull uses it to do a deeper check for the pattern. It tries to find if 
;;  the query pattern conforms to the meaning of data schema (in terms of each value
;;  and your data structrue).

(defmacro try! [& body] `(try ~@body (catch Throwable e# #:error{:msg (ex-message e#)})))

(def my-data-schema [:map [:a :int] [:b :keyword]])
(try!
 (pull/with-data-schema my-data-schema
   (qfn '{:c ?c} ?c)))

;;  The above throws an exception! Because, once you specified a data schema, you only
;;  allow users to query values documented in the schema (i.e. closeness of map). 
;;  Since `:c` is not included in the schema, querying `:c` is invalid.
;;
;; Lasagna-pull try to find all schema problems:

(try!
 (pull/with-data-schema my-data-schema
   (qfn '{(:a :not-found "3") ?} &?)))
;;
;;  The above code also triggers an exception, because `:a` is an `:int` as in the data schema,
;;  while you provide a `:not-found` pattern option value which is not an integer.
;;
;;  ## Real world usage and some history
;;  
;;  In Flybot, we use [fun-map](https://github.com/robertluo/fun-map) extensively.
;;  In a typical server backend, we put everything mutable into a single map called
;;  `system`, including atoms, databases even web server itself. The fun-map library
;;  manages the lifecycles of these components, and injects dependencies among them.
;;
;;  Then, we add database queries into `system`, they relies on databases, so the 
;;  database value (we use Datomic) or connection (if using SQL databases) will be
;;  injected by fun-maps `fnk`, and while developing, we can easily replace the
;;  DB connection by mocked values.
;;
;;  Most queries also require input from user requests, so in the ring handler (of
;;  course, in `system`), we `merge` the HTTP request to `system`. This merged version
;;  then will be used as the source of all possible data.
;;
;;  The next problem is how to let user to fetch. We were not satisfied with conventional
;;  REST style; GraphQL is good, but it adds too much unneccesary middle layers in our
;;  opinion. For example, we need to write many resolvers in order to glue it to backend
;;  data. 
;; 
;;  The first attempt made was [juxt pull](https://github.com/juxt/pull),
;;  I contributed to the 0.2 version and used it in some projects. It allows users to 
;;  construct a data pattern (basic idea and syntax is similar to
;;  [EQL](https://github.com/edn-query-language/eql)) to pull data from the request
;;  -enriched `system`.
;;
;;  However, the syntax of pull does not go very well with deep and complex data
;;  structures (because the database information also translated into fields). It 
;;  just returns a trimmed version of the original map, users often need to walk the
;;  returned value again to find the pieces of information by themselves.
;;
;;  Lasagna-pull introduces a new query pattern in order to address
;;  these problems.
;;
;; ## Development
;;
;; - Run `clojure -T:build ci`.
;; - This project's uses a lot of [rct tests](https://github.com/robertluo/rich-comment-tests).
;;
;; ## About this README
;;
;; This file is generated by `notebook/introduction.clj` 
;; using [clerk-doc](https://github.com/robertluo/clerk-doc).
;;
;; ## License
;; Copyright. © 2022 Flybot Pte. Ltd.
;; Apache License 2.0, http://www.apache.org/licenses/

```clojure
(ns introduction 
  (:require [sg.flybot.pullable :as pull :refer [qfn]])) ;=> nil
```
# Lasagna-pull, query data from deep data structure
[![CI](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml/badge.svg)](https://github.com/flybot-sg/lasagna-pull/actions/workflows/main.yml)
[![Code Coverage](https://codecov.io/gh/flybot-sg/lasagna-pull/branch/master/graph/badge.svg)](https://codecov.io/gh/flybot-sg/lasagna-pull)
[![Clojars](https://img.shields.io/clojars/v/sg.flybot/lasagna-pull.svg)](https://clojars.org/sg.flybot/lasagna-pull)
[![CljDoc](https://cljdoc.org/badge/sg.flybot/lasagna-pull)](https://cljdoc.org/d/sg.flybot/lasagna-pull)
## Problem
 Let's see some data copied from deps.edn:
```clojure
(def utf8? #{"nil" "ascii" "utf8"}
  "opt keyword set for encoding ")

(def depstore
  {:paths ["../bar"
           {"proj/fun" {:mvn/repos {"cljmvn" {:url "https://mvnrepo.com/repo"}}}}]
   :source-paths ["src/clj" "src/java"]
   :reference-aliases
   {:clj {:extra-deps {jp ->jar "jp@1.1"}
          :source-paths ["src/clj"]}
    :cljs {:extra-deps {org.clojure/clojurescript {:git/url "https://github.com/clojure/clojurescript.git" :sha "f32bce32c6be834e128c10972eaad90741c6d868"}}
           :source-paths ["src/cljs"]}
    :frontend {:extra-deps
               {jp ->jar "jp@1.1"
                org.clojure/clojurescript {:git/url "https://github.com/clojure/clojurescript.git" :sha "f32bce32c6be834e128c10972eaad90741c6d868"}
                org.clojure/clojure {:git/url "https://github.com/clojure/clojure.git"}}
               :source-paths ["src/clj" "src/cljs"]}
    :cljs-uberjar {:extra-deps {org.clojure/tools.deps.alpha {:mvn/version "888"}}
                   :main-aliases [:frontend :uberjar]}}
   :main-opts ["-m" "foo.bar"]
   :check-opts ["-A:jayq"]
   :jvm-opts ["-server"
              "-XX:+UnlockDiagnosticVMOptions"
              "-XX:+UnsyncloadClass"
              "-XX:+LogCompilation"
              "-XX:LogFile=bar.log"
              "-XX:ErrorFile=bar.err"
              {:derp ["-XX:+UseZGC"
                      "-XX:+BackgroundCompilation"
                      "-XX:+UnlockExperimentalVMOptions"
                      "-XX:+UseCGroupMemoryLimitForHeap"] :car {:top-level-deps? true :provided-deps? true}}
              ["-XX:+HeapDumpOnOutOfMemoryError"]]
   :reader-opts {:read-cond :allow}
   :aliases
   {:deprecated {:extra-deps {jp                                 ->jar "jp@1.1"}
                 :source-paths                                   ["src/clj"]
                 :deps-file                                      "foo.deps.edn"
                 :repl-options {:expect-promise-break promise/open}
                 :test-runner                                    {:test-paths [:test :test-utility]}
                 :ga @github :utf8? utf8? :force-project true}
    :q {:skip :repl-options}
    :dev
    {:extra-deps {org.clojure/jvm.tools.analyzer {:git/url "https://github.com/clojure/jvm.tools.analyzer.git" :sha "af2ce1b8252ff9f5cc5cd5c5a5ce5f5b2367f4ef"}}
     :runtime-deps {incognito                                      {:git/url "https://github.com/seancorfield/incognito" :sha "c050a960110ade7543c0582a8b38d355bbabcbeb"}}}
    :gain {kk          {:git/url "https://github.com/Plata/file-source.git" :sha "0477e3b6e8d6994b2854aeb52a6c18443f855d79"}
           :test-runner {:test-paths [:test :test-utilities]}
           :top-level-deps? true
           :car nil :utf8? utf8?
           :tootstring true
           :rook :spook :click :clack}
    :source {:extra-deps {neato {:git/url "https://github.com/neato" :sha "23fdssdf"}}
             :source-paths ["src/clj" "src/cljs"]}
    :test
    {:extra-deps {testdeps {:git/url "hsot://github.com/neato.git" :sha "da6666a1a4124aff4f4c4ebb9a95762c8223ce6b"}
                  testdeps2 {:git/url "hsot://github.com/neato2.git"}}
     :test-runner {:test-paths [:test :test-utility]}}})
```

Problem that this library seek to solve is how do you pull particular key? 
The answer is to write smth like `(pull depstore [:source-paths])`. Done!
```clojure
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
              :main-opts ["-m" "kaocha.runner"]}}}}) ;=> #'introduction/data
```
 In `clojure.core`, we have `get` and `get-in` to extract information from a
 map and we are very familiar with them. However, if we have multiple pieces of
 information needs to be extracted from different location of the map, things
 are starting getting tricky.
```clojure
(let [global-path     (get-in data [:deps :paths])
      dev-extra-path  (get-in data [:deps :aliases :dev :extra-paths])
      test-extra-path (get-in data [:deps :aliases :test :extra-paths])]
  (concat global-path dev-extra-path test-extra-path)) ;=> ("src" "dev" "test")
```
This is a simple implementation of a web application on which user interface elements of Pinterest as a reference.
## Bring Lasagna-pull in
 Using lasagna-pull, we can do it in a more intuitive way:


## How to use it
1. Clone the repository in your local folder using the command "git clone https://github.com/tanay2227/Profolio.git"
2. Extract the files 
3. Open the project folder in the terminal
4. Run the command "lein ring server"
5. Open "localhost:3000" in any web browsers of your choice

## Mail Configuration

## This option is used to make sure that the email functionality is working properly or not
## required the default smtp to be filled.

:smtp {:name     "tagomat"
       :address  "localhost"
       :port     25
       :user     nil
       :password nil
       :from     "edmunds@tagomat.de"}
```clojure
((qfn '{:deps {:paths   ?global
               :aliases {:dev  {:extra-paths ?dev}
                         :test {:extra-paths ?test}}}}
      (concat ?global ?dev ?test))
 data) ;=> ("src" "dev" "test")
```
 `pull/qfn` returns a query function, which matches data using a Lasagna pattern.
 A lasagna pattern mimics your data, you specify the pieces of information
 that you interested by using logic variables, they are just symbols starting with
 a `?`.

 > :bulb: `qfn` scans your pattern, and finds them into the data binding them 
 > in your query function. So you can use them directly in the function's body.

 Another frequent situation is selecting subset of a collection, we could use 
 `select-keys` to shrink a map, and `map` it over a sequence of maps.
 Here we use the implicit binding `&?` saved the trouble:
```clojure
((qfn '{:deps {:paths ? :aliases {:dev {:extra-paths ?}}}} &?) data) ;=> {:deps {:paths ["src"], :aliases {:dev {:extra-paths ["dev"]}}}}
```
 The unnamed binding `?` in the pattern is a placeholder, it will appear
 in `&?`.
### Sequence of maps
 It is very common that our data include maps in a sequence, like this:

### 
```clojure
(def person&fruits {:persons [{:name "Alan", :age 20, :sex :male}
                              {:name "Susan", :age 12, :sex :female}]
                    :fruits [{:name "Apple", :in-stock 10}
                             {:name "Orange", :in-stock 0}]
                    :likes [{:person-name "Alan" :fruit-name "Apple"}]}) ;=> #'introduction/person&fruits
```
 The pattern to select inside the sequence of the maps just look like the data itself:
```clojure
((qfn '{:persons [{:name ?}]} &?) person&fruits) ;=> {:persons [{:name "Alan"} {:name "Susan"}]}
```
 by appending a logical variable in the sequence marking vector, we can capture
 a sequence of map.
```clojure
((qfn '{:persons [{:name ?} ?names]} (map :name ?names)) person&fruits) ;=> ("Alan" "Susan")
```
### Filter a value

 Sometimes, we need filtering a map on some keys. It feels very intuitive to specific
 it in your pattern, let's find Alan's age:
```clojure
((qfn '{:persons [{:name "Alan" :age ?age}]} ?age) person&fruits) ;=> nil
```

### Using same named lvar multiple times to achieve a join operation

 If a named lvar bound more than one time, its value has to be the same, otherwise
 all matching fails. We can use this to achieve a join, let's find Alan's favorite fruit
 in-stock value:
```clojure
((qfn '{:persons [{:name "Alan"}]
        :fruits  [{:name ?fruit-name :in-stock ?in-stock}]
        :likes   [{:person-name "Alan" :fruit-name ?fruit-name}]}
      ?in-stock)
 person&fruits) ;=> 10
```
 ## Pattern Options

 In addition to basic query, Lasagna supports pattern options, it is selector
 on the key item part of a pattern, let you fine tune the query.

 ### General options

 These options does not require a predefined set of the arguments.

 #### `:not-found` option

 Probably the easiest one is `:not-found`, it provides a default value if the
 value not found.
```clojure
((qfn '{(:a :not-found 0) ?a} ?a) {:b 3}) ;=> 0
```
#### `:when` option

`:when` option has a predict function as it argument, a match only succeed when this
predict fulfilled by the value.
```clojure
(def q (qfn {(list :a :when odd?) '?a} ?a)) ;=> #'introduction/q
(q {:a 3 :b 3}) ;=> 3
```
 This will fail to match, e.g. returns nil:
```clojure
(q {:a 2 :b 3}) ;=> nil
```
 > Note that the basic filter can also be thought as a `:when` option.

 You can combine the options together, they will be applied by their order:
```clojure
((qfn {(list :a :when even? :not-found 0) '?} &?) {:a -1 :b 3}) ;=> {:a 0}
```
 ### Pattern options requires values be a specific type

 #### `:seq` option
 
 If you are about to match a big sequence, you might want to do pagination. It is essential
 if your values are lazy (sequential). 
```clojure
((qfn '{(:a :seq [5 5]) ?} &?) {:a (range 1000)}) ;=> {:a (5 6 7 8 9)}
```
# Note

The design of putting options in the key part of patterns, allows us to do 
nested query.
```clojure
(def range-data {:a (map (fn [i] {:b i}) (range 100))}) ;=> #'introduction/range-data
((qfn '{(:a :seq [5 5]) [{:b ?} ?bs]} (map :b ?bs)) range-data) ;=> (5 6 7 8 9)
```
#### `:with` and `:batch` options

You may store a function as a value in your map and apply arguments to it when
querying. `:with` can enable you to do this in several ways.

There is an example in `file://demo.py` illustrates the use:

    ## coding: utf-8

    from pathomx.db import ToolStore

    class Demo(ToolStore):

        def prepare(self):
            self.my_para = self.config.get('my_para', 1.0)

        def apply(self, value, condition=None):
            vc = self[value]
            return vc(condition, self.my_para)

        def store(self):
            self[1] = lambda x,p: x*p
            self[2] = lambda x,p: x*p*0.5

    if __name__ == '__main__':
        Demo('demo').run('1', 10, with={'my_para':2})

In the demo.py, `Demo` is a subclass of ToolStore and in the `apply` method, it
runs the function with different arguments. The function query results depends
on the condition, self.my_para is determined by `self.config.get('my_para', 1.0)` by default. In
this way, the user can set self.my_para variable to be any value they want in the config file.
This the first way to use `:with`. The second way is: modify the `Demo().run`
method by adding `with={'my_para':4}`.
```clojure
(defn square [x] (* x x)) ;=> #'introduction/square
((qfn '{(:a :with [5]) ?a} ?a) {:a square}) ;=> 25
```
## A lazy iterable 

And `:batch` will apply many times on the Sequentail, as if it is a sequence:

```clojure
((qfn {(list :a :batch (mapv vector (range 100)) :seq [5 5]) '?a} ?a)
 {:a square}) ;=> (25 36 49 64 81)
```
 These options not just for convinience, if the embeded functions invoked by 
 a query has side effects, these options could do 
 [GraphQL's mutation](https://graphql.org/learn/queries/#mutations).
```clojure
(def a (atom 0)) ;=> #'introduction/a
((qfn {(list :a :with [5]) '?a} ?a)
 {:a (fn [x] (swap! a + x))}) ;=> 5
```
 And the atom has been changed in the repo.
```clojure
@a ;=> 5
```
 ## Malli schema support

 Lasagna-pull has optional for support [malli](https://github.com/metosin/malli) schemas.
 In Clojure, by put malli in your dependencies, it automatically checks your data
 pattern when you make queries to make sure the syntax is correct.

 Your query patterns not only follow the rules of pattern, it also should conform 
 to your data schema. It is especially important when you want to expose your data to
 the external world.
 `with-data-schema` macro let you include your customized data schema (applies to your data),
 then in the body, Lasagna-pull do a deeper check for the pattern. Try to find if 
 the query pattern conforms to the meaning of data schema (in terms of each value
 and your data structrue).
```clojure
(def my-data-schema [:map [:a :int] [:b :keyword]]) ;=> #'introduction/my-data-schema
(pull/with-data-schema my-data-schema
  (qfn '{:c ?c} ?c)) ;throws=>#:error{:type clojure.lang.ExceptionInfo, :message "Wrong pattern", :data {:schema #object[sg.flybot.pullable.schema$pattern_map_schema$reify$reify__22400 0x616fb276 "sg.flybot.pullable.schema$pattern_map_schema$reify$reify__22400@616fb276"], :value {:c ?c}, :errors ({:path [:c], :in [:c], :schema #object[sg.flybot.pullable.schema$pattern_map_schema$reify$reify__22400 0x52c78cf7 "sg.flybot.pullable.schema$pattern_map_schema$reify$reify__22400@52c78cf7"], :value [?c], :type :malli.core/extra-key})}}
```
 The above throws an exception! Because once you specified a data schema, you only
 allow users to query values documented in the schema (i.e. closeness of map). 
 Since `:c` is not included in the schema, querying `:c` is invalid.

 Lasagna-prof try to find all schema problems:
```clojure
(pull/with-data-schema my-data-schema
  (qfn '{(:a :not-found "3") ?} &?)) ;throws=>#:error{:type clojure.lang.ExceptionInfo, :message "Wrong pattern", :data {:schema #object[sg.flybot.pullable.schema$pattern_map_schema$reify$reify__22400 0x66dcab95 "sg.flybot.pullable.schema$pattern_map_schema$reify$reify__22400@66dcab95"], :value {(:a :not-found "3") ?}, :errors ({:path [0 0 1 1], :in [:a 1], :schema :int, :value "3"})}}
```
 The above read also triggers an exception, because `:a` is an `:int` as in the data schema,
 while you provide a `:not-found` pattern option value which is not an integer.

 ## Real world usage and some history
 
 In Flybot, we use [fun-map](https://github.com/robertluo/fun-map) extensively.
 In a typical server backend, we put everything mutable into a single map called
 `system`, including atoms, databases even web server itself. The fun-map library
 manages the lifecycles of these components, injects dependencies among them.

 Then we add database queries into `system`, they relies on databases, so the 
 database value (we use Datomic) or connection (if using SQL databases) will be
 injected by fun-maps `fnk`, and while developing, we can easyly replace the
 db connection by mocked values.

 Most of the queries not just require database itself, also input from user requests,
 so in the ring handler (of course, in `system`), we `merge` http request to 
 `system`, this merged version then will be used to be the source of all possible
 data.

 The next problem is how to let user to fetch, we are not satisfied with conventional
 REST style, GraphQL is good, but it adds too much unneccesary middle layers in my
 opinion, for example, we need to write many resolvers in order to glue it to backend
 data. 

 The first attempt made was [juxt pull](https://github.com/juxt/pull),
 I contributed to the 0.2 version, used it in some projects, allowing users to 
 construct a data pattern (basic idea and syntax is similar to
 [EQL](https://github.com/edn-query-language/eql)) to pull data from the request
 enriched `system`.

 However, the syntax of pull does not go very well with deep and complex data
 structures (because database information also translated into fields), and it 
 just returns a trimmed version of the original map, users often need to walk the
 returned value again to find out the pieces of information by themselves.

 Lasagna-pull introduces a new designed query pattern in order to address
 these problems.

 ## About this README

 This file is generated by `notebook/introduction.clj` 
 using [clerk-doc](https://github.com/robertluo/clerk-doc).

 ## License
 Copyright. © 2022 Flybot Pte. Ltd.
 Apache License 2.0, http://www.apache.org/licenses/

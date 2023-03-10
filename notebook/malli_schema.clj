;;# Introduction to data schema using malli
(ns malli-schema
  (:require 
   [sg.flybot.pullable :as pull]
   [malli.core :as m]))

;; Complex data structures often need data schemas to make sure the users of
;; them to use it correctly. [Malli](https://github.com/metosin/malli) is a very good library
;; to describe them in pure data.
;;
;; Lasagna-pull can use these schemas to check your query pattern, make sure
;; it not only generally syntax correct, but also conforms to your data schema.

(def DepsSchema
  "A sample schema for deps.edn"
  [:map
   [:deps
    [:map 
     [:paths {:optional true} [:vector :string]]
     [:aliases {:optional true}
      [:map-of :keyword
       [:map [:extra-paths {:optional true} [:vector :string]]]]]]]])

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
              :main-opts ["-m" "kaocha.runner"]}
             :clerk
             {:extra-deps {}}}}})

;; Let's conform our data is valid.
(m/explain DepsSchema data)

;; Let's derive an instrumented `pull/query` function from this schema:
(def q (pull/query-of DepsSchema))

;; To catch the exception we are going to throw, let's define a helper macro:
(defmacro try! [& body]
  `(try (do ~@body) (catch Exception e# (ex-data e#))))

(try! (pull/run-query q '{:a ?} data)) 

;; `:a` is not specified in our data schema, and the query pattern can not query
;; a value not in the data schema, that is why this will give us an extra-key error.

;; This version of `query` is quite strict, it checks the schema rigorously, the
;; following pattern fails because `:aliases` is a map, you must specify the content
;; of it.

(try! (pull/run-query q '{:deps {:aliases ?}} data))

;; This will succeed:

(try! (pull/run-query q '{:deps {:aliases {:test {:extra-paths ?}}}} data))

;; All known query options will be checked also, it has to match the meaning
;; of the data schema:

(try! (pull/run-query q '{:deps {:aliases {:clerk {(:extra-paths :not-found ["src"]) ?}}}} data))

;; if you provide a wrong type `:not-found` option argument, it fails:

(try! (pull/run-query q '{:deps {:aliases {:clerk {(:extra-paths :not-found "src") ?}}}} data))

;; We strongly you carefully and throughly make your data schema, if you store a function
;; and want to be invoked by `:with`/`:batch` options, use `:=>` schema, instead of just
;; `fn?`, then, the pattern checker will check the arguments for you.

;; Suppose we have a schema of a number operations:
(def SampleSchema
  [:sequential
   [:map
    [:name :string]
    [:op [:=> [:cat :int] :int]]]])

(def data-operations [{:name "square" :op (fn [x] (* x x))}])

(def sample-q (pull/query-of SampleSchema))

(try! (pull/run-query sample-q '[{:name "square" (:op :with [3]) ?}] data-operations))

;; This is a correct answer.

(try! (pull/run-query sample-q '[{:name "square" (:op :with ["3"]) ?}] data-operations))

;; This fails because we send a string to the `:op` function value as its arguments.

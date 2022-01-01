(ns robertluo.pullable.query-test
  (:require [robertluo.pullable.query :as sut]
            [clojure.test :refer [deftest is testing are]]))

(deftest fn-query
  (are [args data exp] (= exp (sut/run-query (apply sut/fn-query args) data))
    [:a] {:a 1 :b 2}   {:a 1}
    [:a] {:b 2}        {}
    ;;joined query
    [:a :a (sut/fn-query :b)]
    {:a {:b 2 :c 3}}   {:a {:b 2}}

    [:a :a (sut/fn-query :b)]
    {:a {:c 3}}        {:a {}}

    [:a :a (sut/fn-query :b)]
    {:b 1 :c 2}        {}))
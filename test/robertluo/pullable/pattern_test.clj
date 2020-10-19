(ns robertluo.pullable.pattern-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.pullable.core :as core]
   [robertluo.pullable.pattern :as sut]))

(deftest QueryStatement
  (testing "SimpleQuery"
    (is (= (core/->SimpleQuery :a) (sut/-as-query :a))))
  (testing "VectorQuery"
    (is (= (core/->VectorQuery [(core/->SimpleQuery :a)])
           (sut/-as-query [:a]))))
  (testing "JoinQuery"
    (is (= (core/->JoinQuery (core/->SimpleQuery :a)
                             (core/->SimpleQuery :b))
           (sut/-as-query {:a :b})))))


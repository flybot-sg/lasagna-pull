(ns robertluo.pullable.pattern-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.pullable.core :as core]
   [robertluo.pullable.pattern :as sut]))

(deftest QueryStatement
  (testing "SimpleQuery"
    (is (= (core/simple-query :a) (sut/as-query :a))))
  (testing "VectorQuery"
    (is (= (core/vector-query [(core/simple-query {::sut/type :vector} :a)])
           (sut/as-query [:a]))))
  (testing "JoinQuery"
    (is (= (core/join-query (core/simple-query {::sut/type :join-key} :a)
                            (core/simple-query {::sut/type :join-value} :b))
           (sut/as-query {:a :b})))))


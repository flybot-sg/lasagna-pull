(ns robertluo.pullable.pattern-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.pullable.core :as core]
   [robertluo.pullable.pattern :as sut]))

(deftest QueryStatement
  (testing "SimpleQuery"
    (is (= (core/simple-query :a) (sut/as-query :a))))
  (testing "VectorQuery"
    (is (= (core/vector-query [(core/simple-query {::core/type :vector} :a)])
           (sut/as-query [:a]))))
  (testing "JoinQuery"
    (is (= (core/join-query (core/simple-query {::core/type :join} :a)
                            (core/simple-query {::core/type :join} :b))
           (sut/as-query {:a :b})))))


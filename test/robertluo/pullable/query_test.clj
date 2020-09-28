(ns robertluo.pullable.query-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.pullable.query :as sut]))

(deftest SimpleQuery
  (testing "transform"
    (is (= {:a 3}
           (sut/-transform (sut/->SimpleQuery :a) {} {:a 3 :b 5})))))

(deftest JoinQuery
  (testing "transform"
    (is (= {:a {:b 3}}
           (sut/-transform (sut/->JoinQuery
                            (sut/->SimpleQuery :a)
                            (sut/->SimpleQuery :b))
                           {}
                           {:a {:b 3 :c 5}})))))

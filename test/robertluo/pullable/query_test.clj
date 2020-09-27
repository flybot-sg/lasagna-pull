(ns robertluo.pullable.query-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.pullable.query :as sut]))

(deftest join-query
  (testing "join on map structure"
    (is (= {:a {:b 4}}
           ((sut/join-query (sut/simple-query :a) (sut/simple-query :b))
            {:a {:b 4 :c 5}})))
    (is (= {:a {:b ::sut/none}}
           ((sut/join-query (sut/simple-query :a) (sut/simple-query :b))
            {:a {:c 5}})))))

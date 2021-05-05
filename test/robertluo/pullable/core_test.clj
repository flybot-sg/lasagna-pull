(ns robertluo.pullable.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.pullable.core :as sut]))

(deftest simple-query
  (let [q (sut/simple-query :a)]
    (testing "key is just the query's argument"
      (is (= [:a] (sut/-key q))))
    (testing "value of a simple map should be a single value"
      (is (= 3 (sut/-value-of q {:a 3}))))
    (testing "value of a vector map should be a seq of values"
      (is (= [5 3] (sut/-value-of q [{:a 5} {:a 3}]))))))

(deftest vector-query
  (let [q (sut/vector-query [(sut/simple-query :a) (sut/simple-query :b)])]
    (testing "a key of vector query is a seq of its children"
      (is (= [[:a :b]] (sut/-key q))))
    (testing "a value of a plain map for a vector query is a submap of it"
      (is (= {:a 3 :b 4} (sut/-value-of q {:a 3 :b 4 :c 5}))))
    (testing "a value of a vector of maps for a vector query is also a vector of maps"
      (is (= [{:a 3 :b 4} {:a 2 :b ::sut/none}] (sut/-value-of q [{:a 3 :b 4 :c 3} {:a 2 :c 6}]))))))

(deftest join-query
  (let [q (sut/join-query (sut/simple-query :a) (sut/simple-query :b))]
    (testing "key of a join query is the joining of keys of key/value queries"
      (= [[:a :b]] (sut/-key q)))
    (testing "value of a join query is the value of value query"
      (= (sut/-value-of (sut/simple-query :b) {:b 3}) (sut/-value-of q {:a {:b 3}})))))
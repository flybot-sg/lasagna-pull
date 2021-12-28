(ns robertluo.pullable.query-test
  (:require [robertluo.pullable.query :as sut]
            [clojure.test :refer [deftest is testing are]]))

(deftest simple-query-test
  (testing "key of simple query"
    (is (= :a (sut/-key (sut/simple-query :a)))))
  (testing "value of simple query"
    (is (= 4 (sut/-value (sut/simple-query :a) {:a 4}))))
  (testing "matching of simple query"
    (is (= {:a 3} (sut/-matching (sut/simple-query :a) {:a 3 :b 4}))))
  (testing "simple query with child"
    (is (= {:a {:b 4}} 
           (sut/-matching 
            (sut/simple-query :a :a (sut/simple-query :b))
            {:a {:b 4 :c 5}})))))

(deftest vector-query-test
  (let [q (sut/vector-query [(sut/simple-query :a) (sut/simple-query :b)])]
    (testing "key of vector query"
      (is (= [:a :b] (sut/-key q))))
    (testing "value of vector query"
      (is (= [3 5] (sut/-value q {:a 3 :b 5}))))
    (testing "mathing of vector query"
      (is (= {:a 3 :b 4} (sut/-matching q {:a 3 :b 4 :c 5}))))))

(deftest seq-query-test
  (let [q (sut/seq-query (sut/simple-query :a))]
    (testing "key of seq-query"
      (is (= :a (sut/-key q))))
    (testing "value of seq-query"
      (is (= [3 5 7] (sut/-value q [{:a 3} {:a 5 :b 1} {:a 7 :b 2}]))))
    (testing "matching of seq query"
      (is (= [{:a 3} {:a 4} {}] (sut/-matching q [{:a 3 :b 1} {:a 4} {}]))))))

(deftest run-test
  (are [x data exp] (= exp (sut/run x data))
    '{:a ?}           {:a 3 :b 4}          {:a 3}
    '{:a ? :b ?}      {:a 3 :b 4 :c 5}     {:a 3 :b 4}
    '{:a {:b ?}}      {:a {:b 4 :c 5}}     {:a {:b 4}}
    '[{:a ? :b ?}]    [{:a 1} {:a 2 :b 3}] [{:a 1} {:a 2 :b 3}]))

(deftest variable-matching
  (are [x data exp] (= exp (-> (sut/matching x data) second))
    '{:a ?a :b ?b}   {:a 3 :b 4}          '{a 3 b 4}
    '{:a {:b ?b}}    {:a {:b 1}}          '{b 1}
    '[{:a ?} ?a]     [{:a 1} {:a 2 :b 3}] '{a [{:a 1} {:a 2}]}
    ))
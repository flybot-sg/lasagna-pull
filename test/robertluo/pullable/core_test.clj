(ns robertluo.pullable.core-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.pullable.core :as sut]))

(deftest SimpleQuery
  (let [q (sut/simple-query :a)]
    (testing "transform"
      (is (= {:a 3}
             (sut/-transform q {} {:a 3 :b 5}))))
    (testing "Sequence transform"
      (is (= [{:a 3} {:a 4} {:a ::sut/none}]
             (sut/-transform q [] [{:a 3} {:a 4 :b 5} {}]))))))

(deftest JoinQuery
  (let [q (sut/join-query
           (sut/simple-query :a)
           (sut/simple-query :b))]
    (testing "transform simple"
      (is (= {:a {:b 3}}
             (sut/-transform q {} {:a {:b 3 :c 5}}))))
    (testing "transform sequence"
      (is (= {:a [{:b 3}]}
             (sut/-transform q {} {:a [{:b 3}]}))))))

(deftest VectorQuery
  (let [q (sut/vector-query
           [(sut/simple-query :a)
            (sut/simple-query :b)])]
    (testing "transform simple"
      (is (= {:a 3 :b 4}
             (sut/-transform q {} {:a 3 :b 4 :c 5}))))
    (testing "transform sequence"
      (is (= [{:a 3 :b 4} {:a 3 :b ::sut/none}]
             (sut/-transform q [] [{:a 3 :b 4} {:a 3}]))))))

(ns robertluo.pullable.query-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [robertluo.pullable.query :as sut]))

(deftest SimpleQuery
  (let [q (sut/->SimpleQuery :a)]
    (testing "transform"
      (is (= {:a 3}
             (sut/-transform q {} {:a 3 :b 5}))))
    (testing "Sequence transform"
      (is (= [{:a 3} {:a 4} {:a ::sut/none}]
             (sut/-transform q [] [{:a 3} {:a 4 :b 5} {}]))))))

(deftest JoinQuery
  (let [q (sut/->JoinQuery
           (sut/->SimpleQuery :a)
           (sut/->SimpleQuery :b))]
    (testing "transform simple"
      (is (= {:a {:b 3}}
             (sut/-transform q {} {:a {:b 3 :c 5}}))))
    (testing "transform sequence"
      (is (= {:a [{:b 3}]}
             (sut/-transform q {} {:a [{:b 3}]}))))))

(deftest VectorQuery
  (let [q (sut/->VectorQuery
           [(sut/->SimpleQuery :a)
            (sut/->SimpleQuery :b)])]
    (testing "transform simple"
      (is (= {:a 3 :b 4}
             (sut/-transform q {} {:a 3 :b 4 :c 5}))))
    (testing "transform sequence"
      (is (= [{:a 3 :b 4} {:a 3 :b ::sut/none}]
             (sut/-transform q [] [{:a 3 :b 4} {:a 3}]))))))

(deftest AsQuery
  (let [q (sut/->AsQuery
           (sut/->SimpleQuery :a)
           :b)]
    (testing "AsQuery renames a key"
      (is (= {:b 3} (sut/-transform q {} {:a 3}))))))

(deftest NotFoundQuery
  (testing "If not found, replace with value supplied"
    (is (= {:a 0} (sut/-transform (sut/->NotFoundQuery
                                   (sut/->SimpleQuery :a) 0) {} {}))))
  (testing "If specified 'ignore as not-found, it will not appear"
    (is (= {} (sut/-transform (sut/->NotFoundQuery
                                   (sut/->SimpleQuery :a) 'ignore) {} {})))))

(deftest QueryStatement
  (testing "SimpleQuery"
    (is (= (sut/->SimpleQuery :a) (sut/-as-query :a))))
  (testing "VectorQuery"
    (is (= (sut/->VectorQuery [(sut/->SimpleQuery :a)])
           (sut/-as-query [:a]))))
  (testing "JoinQuery"
    (is (= (sut/->JoinQuery (sut/->SimpleQuery :a)
                            (sut/->SimpleQuery :b))
           (sut/-as-query {:a :b}))))
  (testing "query options"
    (is (= (sut/->AsQuery (sut/->SimpleQuery :a) :b)
           (sut/-as-query '(:a :as :b))))
    (is (= (sut/->NotFoundOption (sut/->SimpleQuery :none) 0)
           (sut/-as-query '(:none :not-found 0))))))

(deftest SeqOption
  (testing "seq option returns sequence with offset, limit"
    (is (= [{:a 4} {:a 5} {:a 6}]
           (sut/-transform
            (sut/->SeqOption (sut/->SimpleQuery :a) 1 3)
            []
            [{:a 3} {:a 4} {:a 5} {:a 6} {:a 7}])))
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/-transform
                  (sut/->SeqOption (sut/->SimpleQuery :a) 1 3)
                  []
                  {:a 5})))))

(deftest WithOption
  (testing "with option invoke function of a value"
    (is (= {:a 6}
           (sut/-transform
            (sut/->WithOption (sut/->SimpleQuery :a) [5])
            {}
            {:a inc})))
    (is (thrown? clojure.lang.ExceptionInfo
                 (sut/-transform
                  (sut/->WithOption (sut/->SimpleQuery :a) [5])
                  []
                  {:a 5})))))

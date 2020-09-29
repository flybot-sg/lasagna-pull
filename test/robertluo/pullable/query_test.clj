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

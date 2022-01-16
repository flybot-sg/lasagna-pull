(ns robertluo.pullable.core-test
  (:require [robertluo.pullable.core :as sut]
            [clojure.test :refer [deftest is testing are]])
  (:import [clojure.lang ExceptionInfo]))

(deftest fn-query
  (testing "fn-query select key from a value"
    (are [args data exp] (= exp (sut/run-query (apply sut/fn-query args) data))
      [:a] {:a 1 :b 2}   {:a 1}
      [:a] {:b 2}        {}))
  (testing "fn-query throws data error if data is not a map (associative)"
    (is (thrown? ExceptionInfo (sut/run-query (sut/fn-query :a) 3)))))

(deftest filter-query
  (are [data exp] (= exp (sut/run-query (sut/filter-query (sut/fn-query :a) #(= % 3)) data))
    {:a 3}   {}
    {:a 1}   nil))

(deftest vector-query
  (testing "vector query combines its childrens' results"
    (let [q (sut/vector-query [(sut/fn-query :a) (sut/fn-query :b)])]
      (are [data exp] (= exp (sut/run-query q data))
        {:a 1 :b 2 :c 3}
        {:a 1 :b 2}

        ;;some value missing
        {:d 1, :c 3}
        {})))
  (testing "a single child is equivlent to itself"
    (is (= {:a 1} (sut/run-query (sut/vector-query [(sut/fn-query :a)]) {:a 1}))))
  (testing "has a filter child"
    (let [q (sut/vector-query
             [(sut/fn-query :a) (sut/filter-query (sut/fn-query :b) #(= % 1))])]
      (are [data exp] (= exp (sut/run-query q data))
        {:a 1 :b 1}
        {:a 1}
        
        {:a 1 :b 2}
        {}
        ))))

(deftest seq-query
  (testing "seq query queries by apply q on every item"
    (are [q data exp] (= exp (sut/run-query (sut/seq-query q) data))
      (sut/fn-query :a) [{:a 1} {:a 2 :b 3} {}] [{:a 1} {:a 2} {}]

      (sut/vector-query [(sut/fn-query :a) (sut/fn-query :b)])
      [{:a 1} {:a 2 :b 3} {:c 3}]
      [{:a 1} {:a 2 :b 3} {}]))
  (testing "seq query throws data error on non-sequential data"
    (is (thrown? ExceptionInfo (sut/run-query (sut/seq-query (sut/fn-query :a)) {:a 1})))))

(deftest mk-named-var-query
  (let [bindings (fn [sym-table status]
                   (let [t-sym-table (transient sym-table)
                         a-status (atom status)]
                     [(sut/run-query ((sut/mk-named-var-query t-sym-table a-status 'a) (sut/fn-query :a)) {:a 1 :b 2})
                      (persistent! t-sym-table)]))]
    (are [sym-table status exp] (= exp (bindings sym-table status))
      {}     :fresh    [{:a 1} {'a 1}]
      ;;variable bound, and next query confirmed it
      {'a 1} :bound    [{:a 1} {'a 1}]
      ;;variable invalided by some query
      {}     :invalid  [nil {}]
      ;;variable different from bounded value
      {'a 2} :bound    [nil {}]
      )))

(deftest run-bind
  (are [data exp] (= exp (sut/run-bind #((% 'a) (sut/fn-query :a)) data))
    {:a 1} [{:a 1} {'a 1}]
    {}     [{} {'a nil}]))

(deftest apply-post-seq
  (is (thrown? ExceptionInfo (sut/apply-post #:proc{:type :seq :val 3})))
  (let [f (sut/apply-post #:proc{:type :seq :val [1 3]})]
    (is (thrown? ExceptionInfo (f [:a 3])))
    (is (= [:a [{:a 1} {:a 2} {:a 3}]]
           (f [:a [{:a 0} {:a 1} {:a 2} {:a 3} {:a 4}]])))))

(deftest apply-post-batch
  (is (thrown? ExceptionInfo (sut/apply-post #:proc{:type :batch :val [3]})))
  (let [f (sut/apply-post #:proc{:type :batch :val [[3] [4]]})]
    (is (= [:a [4 5]] (f [:a inc])))))
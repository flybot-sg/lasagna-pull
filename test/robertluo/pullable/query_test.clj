(ns robertluo.pullable.query-test
  (:require [robertluo.pullable.query :as sut]
            [clojure.test :refer [deftest is testing are]]))

(deftest fn-query
  (are [args data exp] (= exp (sut/run-query (apply sut/fn-query args) data))
    [:a] {:a 1 :b 2}   {:a 1}
    [:a] {:b 2}        {}
    ;;joined query
    [:a :a (sut/fn-query :b)]
    {:a {:b 2 :c 3}}   {:a {:b 2}}

    [:a :a (sut/fn-query :b)]
    {:a {:c 3}}        {:a {}}

    [:a :a (sut/fn-query :b)]
    {:b 1 :c 2}        {}))

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
  (testing "inside a fn-query"
    (let [q (sut/vector-query
             [(sut/fn-query :a :a (sut/vector-query
                                   [(sut/fn-query :b) (sut/fn-query :c)]))])]
      (are [data exp] (= exp (sut/run-query q data))
        {:a {:b 1 :c 2 :d 3}}
        {:a {:b 1 :c 2}}
        )))
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
  (are [q data exp] (= exp (sut/run-query (sut/seq-query q) data))
    (sut/fn-query :a) [{:a 1} {:a 2 :b 3} {}] [{:a 1} {:a 2} {}]

    (sut/vector-query [(sut/fn-query :a)(sut/fn-query :b)]) 
    [{:a 1} {:a 2 :b 3} {:c 3}]
    [{:a 1} {:a 2 :b 3} {}]))
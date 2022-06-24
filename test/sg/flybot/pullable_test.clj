(ns sg.flybot.pullable-test
  (:require
   [sg.flybot.pullable :as sut]
   [clojure.test :refer [deftest are testing is]])
  (:import [clojure.lang ExceptionInfo]))

(deftest query
  (testing "`query` can precompile and run"
    (is (= [{:a 1} {}] (sut/run (sut/query '{:a ?}) {:a 1})))))

(deftest compile-query 
  (let [query (sut/query ::pattern)]
    (testing "query provided: just returns it."
      (is (= query (sut/compile-query query))))
    (testing "pattern provided: runs it and returns the query."
      (is (-> (sut/compile-query ::pattern) meta ::sut/compiled?)))))

(deftest validate-data
  (testing "The data complies to schema so returns data."
    (let [data [{:a "foo"} {}]
          schema [:map [:a :string]]]
      (is (= data (sut/validate-data data schema)))))
  (testing "The data does not comply to schema so throws error."
    (let [data [{:a "foo"} {}]
          schema [:map [:a :int]]]
      (is (thrown? ExceptionInfo (sut/validate-data data schema))))))

(deftest ^:integrated run
  (are [data x exp] (= exp (sut/run x data))
    ;;basic patterns
    {:a 1}           '{:a ?}        [{:a 1} {}]
    {:a 1 :b 2 :c 3} '{:a ? :b ?}   [{:a 1 :b 2} {}]

    ;;filtered
    {:a 1 :b 2}      '{:a ? :b 1}   [{} {}]

    ;;filter with a function
    {:a 8 :b 2}      {:a '? :b even?} [{:a 8} {}]

    ;;guard clause
    {:a 2}           {(list :a :when even?) '?}  [{:a 2} {}]

    ;;guard with not-found
    {:a 1}           {(list :a :when even? :not-found 0) '?}  [{:a 0} {}]

    ;;with option
    {:a inc}
    '{(:a :with [3]) ?a}
    [{:a 4} {'?a 4}]

    {:a identity}
    '{(:a :with [{:b 2 :c 3}]) {:b ?}}
    [{:a {:b 2}} {}]

    ;;seq query
    [{:a 1} {:a 2 :b 2} {}]
    '[{:a ?}]
    [[{:a 1} {:a 2} {}] {}]

    ;;nested map query
    {:a {:b 1 :c 2}}
    '{:a {:b ?}}
    [{:a {:b 1}} {}]

    {:a {:b [{:c 1 :d 5} {:c 2}]}}
    '{:a {:b [{:c ?}]}}
    [{:a {:b [{:c 1} {:c 2}]}} {}]

    ;;named variable
    {:a 1 :b 2}
    '{:a ?a}
    [{:a 1} {'?a 1}]

    ;;named variable join
    {:a 2 :b 3}      '{:a ?x :b ?x} [{} {}]


    ;;named join
    {:a 1 :b 1}
    '{:a ?a :b ?a}
    [{:a 1 :b 1} {'?a 1}]

    ;;capture a sequence
    [{:a 1 :b 2} {:a 3 :b 4 :c 5} {:b 6}]
    '[{:a ? :b ?} ?g]
    [[{:a 1 :b 2} {:a 3 :b 4} {:b 6}]
     {'?g [{:a 1 :b 2} {:a 3 :b 4} {:b 6}]}]

    ;;seq option
    (for [x (range 10)]
      {:a x :b x})
    '[{:a ? :b ?} ? :seq [2 3]]
    [[{:a 2 :b 2} {:a 3 :b 3} {:a 4 :b 4}] {}]

    ;;batch option
    {:a identity}
    '{(:a :batch [[3] [{:ok 1}]]) ?a}
    [{:a [3 {:ok 1}]} {'?a [3 {:ok 1}]}]
    ))

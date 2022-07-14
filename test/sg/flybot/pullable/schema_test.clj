(ns sg.flybot.pullable.schema-test
  (:require
   [sg.flybot.pullable.schema :as sut]
   [clojure.test :refer [deftest are testing is]])
(:import
 [clojure.lang ExceptionInfo]))

(deftest transform-key
  (testing "Converts the keyword to a vector of vectors"
    (is (= [[:a nil]]
           (sut/transform-key :a))))
  (testing "Converts the list to a vector of vectors"
    (is (= [[:a nil] [:when even?] [:not-found 0]]
           (sut/transform-key (list :a :when even? :not-found 0))))))

(deftest transform-all-keys
  (testing "Converts all the keys of the maps to vector of vectors."
    (is (= [{[[:a nil] [:not-found 3] [:when odd?]] '?
             [[:b nil] [:with [3]]] {[[:c nil] [:when odd?]] '?c [[:d nil]] even?}}]
           (sut/transform-all-keys
            [{(list :a :not-found 3 :when odd?) '?
              '(:b :with [3]) {(list :c :when odd?) '?c :d even?}}])))))

(deftest pattern-valid?
  (testing "Pattern is wrong so throws error."
    (is (thrown? ExceptionInfo (sut/validate-pattern {:a '? :b []})))
    (is (thrown? ExceptionInfo (sut/validate-pattern '[{:a 3} :with [3]]))))
  (testing "Pattern is valid so returns it."
    (are [p] (= p (sut/validate-pattern p))
    ;;basic patterns
      '{:a ?}
      '{:a ? :b ?}

    ;;filtered
      '{:a ? :b 1}

    ;;filter with a function
      {:a '? :b even?}

    ;;guard clause
      {(list :a :when even?) '?}

    ;;guard with not-found
      {(list :a :when even? :not-found 0) '?}

    ;;with option 
      '{(:a :with [3]) ?a}
      '{(:a :with [{:b 2 :c 3}]) {:b ?}}

    ;;seq query
      '[{:a ?}]

    ;;nested map query 
      '{:a {:b ?}}
      '{:a {:b [{:c ?}]}}

    ;;named variable 
      '{:a ?a}

    ;;named variable join
      '{:a ?x :b {:c ?x}}
      '{:a ?x :b ?x}

    ;;named join 
      '{:a ?a :b ?a}

    ;;capture a sequence 
      '[{:a ? :b ?} ?g]

    ;;seq option 
      '[{:a ? :b ?} ? :seq [2 3]]

    ;;batch option 
      '{(:a :batch [[3] [{:ok 1}]]) ?a})))
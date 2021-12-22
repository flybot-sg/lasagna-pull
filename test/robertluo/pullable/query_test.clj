(ns robertluo.pullable.query-test
  (:require [robertluo.pullable.query :as sut]
            [clojure.test :refer [deftest is are testing]]))

;;To make the tests simpler, I am going to wire the pull expression
;;directly.

(deftest simple-pull
  (testing "Simple structure pull"
    (let [data {:a {:b 1 :c 2 :d 3} :e 4 :f {:g 4}}]
      (are [x exp] (= exp (sut/run x data))
        :e                {:e 4}
        [:e]              {:e 4}
        :g                {}
        {:a :b}           {:a {:b 1}}
        {:a [:c :d]}      {:a {:c 2 :d 3}}
        {:a [:c] :f [:g]} {:a {:c 2} :f {:g 4}}))))

(deftest seq-pull
  (testing "Implicit sequence handling"
    (let [data [{:a 1} {:a 2 :b 3} {:b 4}]]
      (is (= [{:a 1} {:a 2} {}] (sut/run :a data))))
    (let [data [{:a [{:b 1} {:b 2 :c 4} {:c 5}]}]]
      (is (= [{:a [{:b 1} {:b 2} {}]}] (sut/run [{:a [:b]}] data))))))
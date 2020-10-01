(ns robertluo.pullable-test
  (:require
   [robertluo.pullable :as sut]
   [clojure.test :refer [deftest testing is]]))

(deftest pull
  (let [data {:int 8
              :map {:int 9
                    :kw  :foo
                    :recur [{:int 10 :recur [{:int 100 :recur [{:int 1000}]}]}
                            {:int 20 :recur [{:int 200 :recur [{:int 2000 :recur [{:int 20000}]}]}]}]
                    :vec [{:int 5} {:int 8
                                    :kw  :bar}]}
              :fn str
              :fn2 (fn [i] {:val (inc i)})}]
    (testing "simple pull pattern"
      (is (= {:int 8} (sut/pull data :int))))
    (testing "complex pattern"
      (is (= {:int 8 :map {:int 9 :kw :foo}}
             (sut/pull data [:int {:map [:int :kw]}]))))
    (testing "list can provide options"
      (is (= {:none 0} (sut/pull data '(:none :not-found 0)))))
    (testing "join with :seq key is an explicit seq, will pull over its element"
      (is (= {:map {:vec [{:int 5} {:int 8}]}}
             (sut/pull data {:map {:vec :int :seq []}}))))
    (testing ":limit and :offset will give a pagination"
      (is (= {:map {:vec [{:int 8}]}}
             (sut/pull data '{:map {:vec [(:int :seq [1 1])]}}))))
    (testing "can pull in a root sequence"
      (is (= [{:int 8} {:int 8}]
             (sut/pull [data data] [:int]))))
    (testing "pull with a :with option, will call the value as a function"
      (is (= {:fn "hello world"}
             (sut/pull data '(:fn :with ["hello" " " "world"])))))
    (testing ":with option can be pulled as if it is a normal one"
      (is (= {:fn2 {:val 8}}
             (sut/pull data '(:fn2 :with [7])))))))

#_(deftest join-as-key
  (testing "when join as key of a pattern"
    (let [data {:a {:b {:c 5}}}]
      (is (= {:a {:c 5}}
             (sut/pull data '{{:a :b} :c}))))))

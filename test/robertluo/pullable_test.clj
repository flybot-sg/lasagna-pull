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
      (is (= {:none 0} (sut/pull data ['(:none :not-found 0)]))))
    (testing "join with :seq key is an explicit seq, will pull over its element"
      (is (= {:map {:vec [{:int 5} {:int 8}]}}
             (sut/pull data {:map {:vec :int :seq []}}))))
    (testing ":limit and :offset will give a pagination"
      (is (= {:map {:vec [{:int 8}]}}
             (sut/pull data {:map {:vec [:int] :seq [1 1]}}))))
    (testing "recursive pull"
      (is (= {:map {:recur [{:int 10 :recur [{:int 100 :recur [{:int 1000}]}]}
                            {:int 20 :recur [{:int 200 :recur [{:int 2000}]}]}]}}
             (sut/pull data {:map {:recur :int :seq [] :depth 2}}))))
    (testing "when pull data not as expected shape, it still can returns other part."
      (let [exp (sut/pull data [:int {:map :int :seq []}])]
        (is (= [8 :map] ((juxt :int #(get-in % [:map :error/key])) exp)))))
    (testing "can pull in a root sequence"
      (is (= [{:int 8} {:int 8}]
             (sut/pull [data data] '([:int] :seq [])))))
    (testing "pull with a :with option, will call the value as a function"
      (is (= {:fn "hello world"}
             (sut/pull data '(:fn :with ["hello" " " "world"])))))
    (testing ":with option can be pulled as if it is a normal one"
      (is (= {:fn2 {:val 8}}
             (sut/pull data '(:fn2 :with [7])))))
    (testing ":batch option will batch :with calls"
      (is (= {:fn2 [{:val 8} {:val 9}]}
             (sut/pull data '(:fn2 :batch [[7] [8]])))))))

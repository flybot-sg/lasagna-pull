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
                                    :kw  :bar}]} }]
    (testing "simple pull pattern"
      (is (= {:int 8} (sut/pull data [:int]))))
    (testing "complex pattern"
      (is (= {:int 8 :map {:int 9 :kw :foo}}
             (sut/pull data [:int {:map [:int :kw]}]))))
    (testing "list can provide options"
      (is (= {:none 0} (sut/pull data ['(:none :not-found 0)]))))
    (testing "join with :seq key is an explicit seq, will pull over its element"
      (is (= {:map {:vec [{:int 5} {:int 8}]}}
             (sut/pull data [{:map {:vec :int :seq []}}]))))
    (testing ":limit and :offset will give a pagination"
      (is (= {:map {:vec [{:int 8}]}}
             (sut/pull data {:map [{:vec [:int] :seq [1 1]}]}))))
    (testing "recursive pull"
      (is (= {:map {:recur [{:int 10 :recur [{:int 100 :recur [{:int 1000}]}]}
                            {:int 20 :recur [{:int 200 :recur [{:int 2000}]}]}]}}
             (sut/pull data [{:map [{:recur [:int] :seq [] :depth 2}]}]))))
    (testing "when pull data not as expected shape, it still can returns other part."
      (let [exp (sut/pull data [:int {:map [:int] :seq []}])]
        (is (= [8 :map] ((juxt :int #(get-in % [:map :error/key])) exp)))))))
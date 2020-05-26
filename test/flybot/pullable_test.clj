(ns flybot.pullable-test
  (:require
   [flybot.pullable :as sut]
   [clojure.test :refer [deftest testing is]]))

(deftest pull
  (let [data {:int 8
              :map {:int 9
                    :kw  :foo
                    :vec [{:int 5} {:int 8
                                    :kw  :bar}]}}]
    (testing "simple pull pattern"
      (is (= {:int 8} (sut/pull data [:int]))))
    (testing "complex pattern"
      (is (= {:int 8 :map {:int 9 :kw :foo}}
             (sut/pull data [:int {:map [:int :kw]}]))))
    (testing "list can provide options"
      (is (= {:none 0} (sut/pull data ['(:none :not-found 0)]))))
    (testing "join with :seq? key is an explicit seq, will pull over its element"
      (is (= {:map {:vec [{:int 5} {:int 8}]}}
             (sut/pull data [{:map [{:vec [:int] :seq? true}]}]))))))
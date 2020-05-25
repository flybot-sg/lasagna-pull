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
             (sut/pull data [:int {:map [:int :kw]}]))))))
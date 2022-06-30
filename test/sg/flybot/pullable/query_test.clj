(ns sg.flybot.pullable.query-test 
  (:require
   [sg.flybot.pullable.query :as sut]
   [clojure.test :refer [deftest is are testing]]))

(deftest join-query
  (let [q (sut/join-query (sut/fn-query :a) (sut/fn-query :b))]
    (testing "successful join"
      (is (= {:a {:b 1}} (sut/run-query q {:a {:b 1 :c 2}}))))
    (testing "not joinable data"
      (are [exp data] (= exp (sut/run-query q data))
        {:a {}} {:a {:c 1}}
        {}      {:b 1}))))

(deftest vector-query
  (let [q (sut/vector-query [(sut/fn-query :a) (sut/fn-query :b)])]
    (are [exp data] (= exp (sut/run-query q data))
      {:a 1 :b 2}    {:a 1 :c 3 :b 2}
      {:a 1}         {:a 1 :c 3})))

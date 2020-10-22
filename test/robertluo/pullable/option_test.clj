(ns robertluo.pullable.option-test
  (:require
   [clojure.test :refer [deftest is]]
   [robertluo.pullable.option]
   [robertluo.pullable.core :as core])
  (:import
   [clojure.lang ExceptionInfo]))

(defn option-for
  ([opt-key opt-arg data]
   (option-for (core/->SimpleQuery :a) opt-key opt-arg data))
  ([q opt-key opt-arg data]
   (-> (core/create-option #:option{:query q :type opt-key :arg opt-arg})
       (core/-transform (empty data) data))))

(deftest as-option
  (is (= {:b 3} (option-for :as :b {:a 3}))))

(deftest not-found-option
  (is (= {:a 0} (option-for :not-found 0 {}))))

(deftest exception-option
  (is (thrown? ExceptionInfo (option-for :exception 'NONE {})))
  (let [q (reify core/Query
            (-key [_] [:a])
            (-value-of [_ _] (throw (Exception. "!"))))]
    (is (= {:a ::ok}
           (option-for q :exception (constantly ::ok) {})))))

(deftest with-option
  (is (= {:a 3} (option-for :with [2] {:a inc})))
  (is (thrown? ExceptionInfo (option-for :with [2] {:a 3})))
  (is (thrown? ExceptionInfo (option-for :with 2 {:a inc}))))

(deftest seq-option
  (is (= [{:a 1} {:a 2}]
         (option-for :seq [1 2] [{:a 0} {:a 1} {:a 2} {:a 3}])))
  (is (thrown? ExceptionInfo (option-for :seq 1 {})))
  (is (thrown? ExceptionInfo (option-for :seq [:a] {})))
  (is (thrown? ExceptionInfo (option-for :seq [1 2] {:a 3}))))

(deftest batch-option
  (is (= {:a [2 3]}
         (option-for :batch [[1] [2]] {:a inc})))
  (is (thrown? ExceptionInfo (option-for :batch 1 {}))))

(deftest when-option
  (is (= {:a [0 2 4 6 8]} (option-for :when even? {:a (range 10)}))))

(deftest group-by-option
  (is (= {:a {true [0 2 4 6 8] false [1 3 5 7 9]}}
         (option-for :group-by even? {:a (range 10)}))))

(deftest order-by-option
  (is (= [{:a 3 :b 2} {:a 4 :b 2} {:a 5 :b 2}]
         (option-for (core/->VectorQuery
                      [(core/->SimpleQuery :a)
                       (core/->SimpleQuery :b)])
                     :order-by :a [{:a 4 :b 2} {:a 3 :b 2} {:a 5 :b 2}]))))

(ns robertluo.pullable.option-test
  (:require
   [clojure.test :refer [deftest is]]
   [robertluo.pullable.option]
   [robertluo.pullable.query :as core]))

(defn option-for
  [q opt-key opt-arg data]
  (-> (core/create-option #:option{:query q :type opt-key :arg opt-arg})
      (core/-transform (empty data) data)))

(deftest as-option
  (is (= {:b 3} (option-for (core/->SimpleQuery :a) :as :b {:a 3}))))

(deftest not-found-option
  (is (= {:a 0} (option-for (core/->SimpleQuery :a) :not-found 0 {}))))

(deftest with-option
  (is (= {:a 3} (option-for (core/->SimpleQuery :a) :with [2] {:a inc}))))

(deftest seq-option
  (is (= [{:a 1} {:a 2}]
         (option-for (core/->SimpleQuery :a) :seq [1 2] [{:a 0} {:a 1} {:a 2} {:a 3}]))))

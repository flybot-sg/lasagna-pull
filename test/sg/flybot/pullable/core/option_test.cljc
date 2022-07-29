(ns sg.flybot.pullable.core.option-test
  (:require [sg.flybot.pullable.core.option :as sut]
            [sg.flybot.pullable.util :refer [error?]]
            [clojure.test :refer [deftest is]])
  #?(:clj (:import [clojure.lang ExceptionInfo])))

(deftest apply-post-seq
  (is (thrown? ExceptionInfo (sut/apply-post #:proc{:type :seq :val 3})))
  (let [f (sut/apply-post #:proc{:type :seq :val [1 3]})]
    (is (error? (second (f [:a 3]))))
    (is (= [:a [{:a 1} {:a 2} {:a 3}]]
           (f [:a [{:a 0} {:a 1} {:a 2} {:a 3} {:a 4}]])))))

(deftest apply-post-batch
  (is (thrown? ExceptionInfo (sut/apply-post #:proc{:type :batch :val [3]})))
  (let [f (sut/apply-post #:proc{:type :batch :val [[3] [4]]})]
    (is (= [:a [4 5]] (f [:a inc])))))

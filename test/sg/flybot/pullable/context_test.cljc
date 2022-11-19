(ns sg.flybot.pullable.context-test
  (:require
   [sg.flybot.pullable.context :as sut]
   [clojure.test :refer [deftest is testing]]))

(deftest run-context
  (testing "a stateless context do nothing but just pass by context"
    (is (= [] (sut/run-bind (sut/stateless-context identity) :a)))))
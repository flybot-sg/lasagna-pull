(ns sg.flybot.pullable.parameter-test
  (:require
   [sg.flybot.pullable.parameter :as sut]
   [clojure.test :refer [deftest are testing]]))

(deftest pred-of
  (testing "pred-of transform a value expression to a predict function"
    (are [exp value x v] (= exp ((sut/pred-of (constantly value) x) {} v))
      true  nil even? 2
      false nil even? 3

      true  2   '*a   2
      false 3   '*a   2
      
      true  3   [#(> % %2) ['*a]] 2
      false 1   [#(> % %2) ['*a]] 2
      
      true  nil  #(> % 2)  3
      false nil  #(> % 2)  1)))
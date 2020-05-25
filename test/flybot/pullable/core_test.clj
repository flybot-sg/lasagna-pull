(ns flybot.pullable.core-test
  (:require
   [flybot.pullable.core :as sut]
   [clojure.test :refer [deftest is]]))

(deftest general
  (is (= 5 (+ 2 3))))
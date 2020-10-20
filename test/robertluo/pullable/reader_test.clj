(ns robertluo.pullable.reader-test
  (:require
   [clojure.test :refer [deftest is]]
   [robertluo.pullable.reader :as sut]))

(deftest depth-reader
  (is (= '{:child [:age {:child [:age]}]}
         (sut/expand-depth [{:child [:age]} 1]))))

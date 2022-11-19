(ns sg.flybot.pullable.executor-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [sg.flybot.pullable.executor :as sut]))

(deftest atom-executor
  (testing "atom executor collect workloads and run it all"
    (let [a-value (atom {})
          executor (sut/atom-executor a-value)]
      (doseq [workload [[assoc :a 3] [assoc :b 4] [assoc :a 1]]]
        (sut/-receive-effect executor workload))
      (sut/-run! executor)
      
      (is true (sut/-empty? executor))
      (is (= {:a 1 :b 4} @a-value))
      (is (= @a-value (sut/-result executor))))))
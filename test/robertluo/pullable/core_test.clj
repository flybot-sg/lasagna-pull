(ns ^:unit robertluo.pullable.core-test
  (:require
   [robertluo.pullable.core :as sut]
   [clojure.test :refer [deftest testing is]]))

(deftest CoreQuery
  (testing "simple query just return a map"
    (is (= {:a 3}
           (sut/-select (sut/map->CoreQuery {:key :a}) {:a 3}))))
  (testing "when there is an exception, it still return with ex-handler called"
    (is (= {:a :a}
           (sut/-select (sut/map->CoreQuery {:key :a :processors ["ok"]
                                             :ex-handler :error/key}) {})))))

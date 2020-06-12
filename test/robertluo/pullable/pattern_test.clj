(ns ^:unit robertluo.pullable.pattern-test
  (:require
   [robertluo.pullable.pattern :as sut]
   [clojure.test :refer [deftest is testing]]))

(deftest expand-depth
  (is (= {:a [:b {:a [:b] :depth 2 :seq [] :not-found ::sut/ignore}] :seq []}
         (sut/expand-depth 3 {:a [:b] :seq []}))))

(deftest Queryable
  (testing "nil expand to empty query"
    (is (= {} (sut/-to-query nil))))
  (testing "normal object expand to key"
    (is (= {:key :a} (sut/-to-query :a))))
  (testing "vector expand to query sequence"
    (is (= {:children [ {:key :a} {}]} (sut/-to-query [:a nil]))))
  (testing "list provide options"
    (is (= {:key :a :options [[:op true]]}
           (sut/-to-query '(:a :op true)))))
  (testing "map will provide key, children, options at the same time"
    (is (= {:key :a :children [{:key :b} {:key :c}] :options {:op true}}
           (sut/-to-query {:a [:b :c] :op true})))))

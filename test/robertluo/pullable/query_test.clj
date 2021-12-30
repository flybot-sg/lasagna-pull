(ns robertluo.pullable.query-test
  (:require [robertluo.pullable.query :as sut]
            [clojure.test :refer [deftest is testing are]]))

(deftest simple-query
  (testing "simple query returns a single result"
    (are [q exp] (= exp (sut/run-query q {:a 1 :b {:c 2} :d 3}))
      (sut/simple-query :a) {:a 1}
      (sut/simple-query :e) {}
      (sut/simple-query :b :b (sut/simple-query :c)) {:b {:c 2}})))

(deftest vector-query
  (testing "vector query returns merged results"
    (are [q exp] (= exp (sut/run-query q {:a 1 :b {:c 2} :d 3}))
      (sut/vector-query [(sut/simple-query :a) (sut/simple-query :d)]) {:a 1 :d 3}
      (sut/vector-query [(sut/simple-query :b :b (sut/simple-query :c))(sut/simple-query :d)]) {:b {:c 2} :d 3}
      )))

(deftest seq-query
  (testing "seq query apply maps on a collection"
    (are [q exp] (= exp (sut/run-query q [{:a 1 :b {}} {:a 2 :b {:c 3} :d 4}]))
      (sut/seq-query (sut/simple-query :a)) [{:a 1} {:a 2}]
      )))

(deftest composite-query
  (let [q (sut/seq-query
           (sut/simple-query :a :a (sut/seq-query (sut/vector-query [(sut/simple-query :b) (sut/simple-query :c)]))))
        data [{:a [{:b 1 :c 2 :e 3} {:b 10}]} {:a [{:b 3 :c 4 :e 5}]}]]
    (testing "a composite query including different queries type and try run it"
      (is (= [{:a [{:b 1 :c 2} {:b 10}]} {:a [{:b 3 :c 4}]}] (sut/run-query q data))))))

#_(deftest run-test
  (are [x data exp] (= exp (sut/run x data))
    '{:a ?}           {:a 3 :b 4}          {:a 3}
    '{:a ? :b ?}      {:a 3 :b 4 :c 5}     {:a 3 :b 4}
    '{:a {:b ?}}      {:a {:b 4 :c 5}}     {:a {:b 4}}
    '[{:a ? :b ?}]    [{:a 1} {:a 2 :b 3}] [{:a 1} {:a 2 :b 3}]))

#_(deftest variable-matching
  (are [x data exp] (= exp (-> (sut/matching x data) second))
    '{:a ?a :b ?b}   {:a 3 :b 4}          '{a 3 b 4}
    '{:a {:b ?b}}    {:a {:b 1}}          '{b 1}
    '[{:a ?} ?a]     [{:a 1} {:a 2 :b 3}] '{a [{:a 1} {:a 2}]}
    ))
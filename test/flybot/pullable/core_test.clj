(ns flybot.pullable.core-test
  (:require
   [flybot.pullable.core :as sut]
   [clojure.test :refer [deftest testing is]]))

(deftest query
  (let [data {:foo "bar" :baz {:foo2 3 :baz2 'ok}}]
    (testing "query with a single key will just returns a single kv map"
      (is (= {:foo "bar"} (sut/-select (sut/query {:key :foo}) data))))
    (testing "query with nil key but with children"
      (is (= {:foo "bar"} (sut/-select (sut/query {:children [(sut/query {:key :foo})]}) data))))
    (testing "when query has children, it will pass sub data to them"
      (is (= {:baz {:foo2 3}} (sut/-select (sut/query {:key :baz :children [(sut/query {:key :foo2})]}) data)))
      (is (= {:baz {:foo2 3 :baz2 'ok}}
             (sut/-select (sut/query {:key :baz :children [(sut/query {:key :foo2})
                                                           (sut/query {:key :baz2})]}) data))))))

(deftest seq-query
  (testing "a seq query returns sequence of its key"
    (is (= [{:a 3 :b ::sut/not-found} {:a 8 :b 4}]
           (sut/-select (sut/query {:children [(sut/query {:key :a})
                                               (sut/query {:key :b})]}
                                   {:seq? true})
                        [{:a 3} {:a 8 :b 4}])))))

(deftest mk-processors
  (testing "for empty options, create core option"
    (is (= {::sut/core (sut/->CoreProcessor)}
           (sut/mk-processors {}))))
  (testing "for seq? option, create a seq processor"
    (is (= {::sut/core (sut/->SeqProcessor)}
           (sut/mk-processors {:seq? true}))))
  (testing "for not-found option, create a not found processor"
    (is (= {::sut/core (sut/->CoreProcessor) ::sut/not-found (sut/->NotFoundProcessor :foo)}
           (sut/mk-processors {:not-found :foo})))))

(deftest not-found-option
  (testing "a query with :not-found specified will return it"
    (is (= {:bar 0}
           (sut/-select (sut/query {:key :bar} {:not-found 0}) {})))))

(deftest expand-depth
  (is (= {:a [:b {:a [:b] :depth 2 :seq? true :not-found ::sut/ignore}] :seq? true}
         (sut/expand-depth 3 {:a [:b] :seq? true}))))

(deftest pattern->query
  (testing "nil pattern makes an empty query"
    (is (= (sut/query {}) (sut/pattern->query nil))))
  (testing "single element pattern makes a simple query"
    (is (= (sut/query {:key :a}) (sut/pattern->query :a))))
  (testing "root vector will make a root empty query with children"
    (is (= (sut/query {:children [(sut/query {:key :a}) (sut/query {:key :b})]})
           (sut/pattern->query [:a :b]))))
  (testing "map makes a query with key and children"
    (is (= (sut/query {:key :a :children [(sut/query {:key :b})]})
           (sut/pattern->query {:a [:b]}))))
  (testing "list can provide options"
    (is (= (sut/query {:key :int} {:not-found 0})
           (sut/pattern->query '(:int :not-found 0)))))
  (testing "map can have options which will be inside query"
    (is (= (sut/query {:key :a :children [(sut/query {:key :b})]} 
                      {:seq? true})
           (sut/pattern->query {:a [:b] :seq? true}))))
  (testing "depth option will produce more children itself"
    (is (= (sut/query {:key :a :children [(sut/query {:key :b})
                                          (sut/query {:key :a
                                                      :children [(sut/query {:key :b})]}
                                                     {:not-found ::sut/ignore
                                                      :seq? true})]}
                      {:seq? true})
           (sut/pattern->query {:a [:b] :depth 1 :seq? true})))))

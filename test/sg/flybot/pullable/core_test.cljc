(ns sg.flybot.pullable.core-test
  (:require [sg.flybot.pullable.core :as sut]
            [sg.flybot.pullable.util :refer [error?]]
            [clojure.test :refer [deftest is testing are]]))

(deftest fn-query
  (testing "fn-query select key from a value"
    (are [args data exp] (= exp (sut/run-query (apply sut/fn-query args) data))
      [:a] {:a 1 :b 2}   {:a 1}
      [:a] {:b 2}        {}))
  (testing "fn-query throws data error if data is not a map (associative)"
    (is (error? (:a (sut/run-query (sut/fn-query :a) 3))))))

(deftest join-query
  (are [data exp] (= exp (sut/run-query (sut/join-query (sut/fn-query :a) (sut/fn-query :b)) data))
    {:a {:b 1 :c 2}}    {:a {:b 1}}
    {:c 3}              {})
  (testing "join with vec"
    (is (= {:a {:b 1}} 
           (sut/run-query 
            (sut/join-query (sut/fn-query :a) (sut/vector-query [(sut/fn-query :b)])) {:a {:b 1}}))))
  (testing "error in parent query will shortcut child query"
    (is (error?
         (-> :a
             sut/fn-query
             (sut/join-query (sut/fn-query :b))
             (sut/run-query 3)
             :a)))))

(deftest filter-query
  (are [data exp] (= exp (sut/run-query (sut/filter-query (sut/fn-query :a) #(= %2 3)) data))
    {:a 3}   {}
    {:a 1}   nil))

(deftest vector-query
  (testing "vector query combines its childrens' results"
    (let [q (sut/vector-query [(sut/fn-query :a) (sut/fn-query :b)])]
      (are [data exp] (= exp (sut/run-query q data))
        {:a 1 :b 2 :c 3}
        {:a 1 :b 2}

        ;;some value missing
        {:d 1, :c 3}
        {})))
  (testing "a single child is equivlent to itself"
    (is (= {:a 1} (sut/run-query (sut/vector-query [(sut/fn-query :a)]) {:a 1}))))
  (testing "has a filter child"
    (let [q (sut/vector-query
             [(sut/fn-query :a) (sut/filter-query (sut/fn-query :b) #(= %2 1))])]
      (are [data exp] (= exp (sut/run-query q data))
        {:a 1 :b 1}
        {:a 1}

        {:a 1 :b 2}
        nil))))

(deftest post-process-query
  (is (= {:a 3} (sut/run-query (sut/post-process-query (sut/fn-query :a) (constantly [:a 3])) {:a 1}))))

(deftest seq-query
  (testing "seq query queries by apply q on every item"
    (are [q data exp] (= exp (sut/run-query (sut/seq-query q) data))
      (sut/fn-query :a) [{:a 1} {:a 2 :b 3} {}] [{:a 1} {:a 2} {}]

      (sut/vector-query [(sut/fn-query :a) (sut/fn-query :b)])
      [{:a 1} {:a 2 :b 3} {:c 3}]
      [{:a 1} {:a 2 :b 3} {}]))
  (testing "seq query throws data error on non-sequential data"
    (is (error? (sut/run-query (sut/seq-query (sut/fn-query :a)) {:a 1})))))

(deftest apply-post-watch 
  (testing "watch option registers a watcher on IRef, 
            when watcher returns nil, it removes the watcher"
    (let [a    (atom 1)
          rslt (atom nil)
          f    (fn [ov nv] (when nv (reset! rslt [ov nv])))
          q    (sut/decorate-query (sut/fn-query :a) [[:watch f]])]
      (is (error? (:a (sut/run-query q {:a 3}))))
      (is (= {:a 1} (sut/run-query q {:a a})))
      (reset! a 2)
      (is (= [1 2] @rslt))
      (reset! a false)
      (is (= [1 2] @rslt))
      (reset! a 3)
      (is (= [1 2] @rslt)))))

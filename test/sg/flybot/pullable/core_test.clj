(ns sg.flybot.pullable.core-test
  (:require [sg.flybot.pullable.core :as sut]
            [clojure.test :refer [deftest is testing are]])
  (:import [clojure.lang ExceptionInfo]))

(deftest fn-query
  (testing "fn-query select key from a value"
    (are [args data exp] (= exp (sut/run-q (apply sut/fn-query args) data))
      [:a] {:a 1 :b 2}   {:a 1}
      [:a] {:b 2}        {}))
  (testing "fn-query throws data error if data is not a map (associative)"
    (is (thrown? ExceptionInfo (sut/run-q (sut/fn-query :a) 3)))))

(deftest join-query
  (are [data exp] (= exp (sut/run-q (sut/join-query (sut/fn-query :a) (sut/fn-query :b)) data))
    {:a {:b 1 :c 2}}    {:a {:b 1}}
    {:c 3}              {}
    ))

(deftest filter-query
  (are [data exp] (= exp (sut/run-q (sut/filter-query (sut/fn-query :a) #(= % 3)) data))
    {:a 3}   {}
    {:a 1}   nil))

(deftest vector-query
  (testing "vector query combines its childrens' results"
    (let [q (sut/vector-query [(sut/fn-query :a) (sut/fn-query :b)])]
      (are [data exp] (= exp (sut/run-q q data))
        {:a 1 :b 2 :c 3}
        {:a 1 :b 2}

        ;;some value missing
        {:d 1, :c 3}
        {})))
  (testing "a single child is equivlent to itself"
    (is (= {:a 1} (sut/run-q (sut/vector-query [(sut/fn-query :a)]) {:a 1}))))
  (testing "has a filter child"
    (let [q (sut/vector-query
             [(sut/fn-query :a) (sut/filter-query (sut/fn-query :b) #(= % 1))])]
      (are [data exp] (= exp (sut/run-q q data))
        {:a 1 :b 1}
        {:a 1}
        
        {:a 1 :b 2}
        {}
        ))))

(deftest post-process-query
  (is (= {:a 3} (sut/run-q (sut/post-process-query (sut/fn-query :a) (constantly [:a 3])) {:a 1}))))

(deftest seq-query
  (testing "seq query queries by apply q on every item"
    (are [q data exp] (= exp (sut/run-q (sut/seq-query q) data))
      (sut/fn-query :a) [{:a 1} {:a 2 :b 3} {}] [{:a 1} {:a 2} {}]

      (sut/vector-query [(sut/fn-query :a) (sut/fn-query :b)])
      [{:a 1} {:a 2 :b 3} {:c 3}]
      [{:a 1} {:a 2 :b 3} {}]))
  (testing "seq query throws data error on non-sequential data"
    (is (thrown? ExceptionInfo (sut/run-q (sut/seq-query (sut/fn-query :a)) {:a 1})))))

(deftest mk-named-var-query
  (let [bindings (fn [sym-table status]
                   (let [t-sym-table (transient sym-table)
                         a-status (atom status)]
                     [(sut/run-q ((sut/mk-named-var-query t-sym-table a-status 'a) (sut/fn-query :a)) {:a 1 :b 2})
                      (persistent! t-sym-table)]))]
    (are [sym-table status exp] (= exp (bindings sym-table status))
      {}     :fresh    [{:a 1} {'a 1}]
      ;;variable bound, and next query confirmed it
      {'a 1} :bound    [{:a 1} {'a 1}]
      ;;variable invalided by some query
      {}     :invalid  [nil {}]
      ;;variable different from bound value
      {'a 2} :bound    [nil {}]
      )))

(deftest run-bind
  (are [data exp] (= exp (sut/run-bind #((% 'a) (sut/fn-query :a)) data))
    {:a 1} [{:a 1} {'a 1}]
    {}     [{} {'a nil}]))

(deftest apply-post-seq
  (is (thrown? ExceptionInfo (sut/apply-post #:proc{:type :seq :val 3})))
  (let [f (sut/apply-post #:proc{:type :seq :val [1 3]})]
    (is (thrown? ExceptionInfo (f [:a 3])))
    (is (= [:a [{:a 1} {:a 2} {:a 3}]]
           (f [:a [{:a 0} {:a 1} {:a 2} {:a 3} {:a 4}]])))))

(deftest apply-post-batch
  (is (thrown? ExceptionInfo (sut/apply-post #:proc{:type :batch :val [3]})))
  (let [f (sut/apply-post #:proc{:type :batch :val [[3] [4]]})]
    (is (= [:a [4 5]] (f [:a inc])))))

(deftest apply-post-watch
  (is (thrown? ExceptionInfo (sut/apply-post #:proc{:type :watch :val 3})))
  (testing "watch option registers a watcher on IRef, 
            when watcher returns nil, it removes the watcher"
    (let [a    (atom 1)
          rslt (atom nil)
          f    (fn [ov nv] (when nv (reset! rslt [ov nv])))
          q    (sut/decorate-query (sut/fn-query :a) [[:watch f]])]
      (is (thrown? ExceptionInfo (sut/run-q q {:a 3})))
      (is (= {:a 1} (sut/run-q q {:a a})))
      (reset! a 2)
      (is (= [1 2] @rslt))
      (reset! a false)
      (is (= [1 2] @rslt))
      (reset! a 3)
      (is (= [1 2] @rslt)))))
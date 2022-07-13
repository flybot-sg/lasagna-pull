(ns sg.flybot.pullable.pattern-test
  (:require
   [sg.flybot.pullable.pattern :as sut]
   [clojure.test :refer [deftest are testing is]])
  (:import
   [clojure.lang ExceptionInfo]))

(deftest ->query
  (testing "If pattern is wrong, ->query can complain"
    (is (thrown? ExceptionInfo (sut/->query identity 3)))
    (is (thrown? ExceptionInfo (sut/->query identity '[{:a 3} :with [3]]))))
  (are [m exp] (= exp (sut/->query #(concat % [:ok]) m))
    '{:a ?}                 [:vec [[:fn :a :ok]] :ok]
    '{:a ? :b ?}            [:vec [[:fn :a :ok] [:fn :b :ok]] :ok]
    '{:a ?a}                [:vec [[:named [:fn :a :ok] '?a :ok]] :ok]
    '{:a {:b ?}}            [:vec [[:join [:fn :a :ok] [:vec [[:fn :b :ok]] :ok] :ok]] :ok]
    '{:b 2}                 [:vec [[:filter [:fn :b :ok] 2 :ok]] :ok]
    '{(:b :not-found 3) ?b} [:vec [[:named [:deco [:fn :b :ok] [[:not-found 3]] :ok] '?b :ok]] :ok]
    '{(:a :with [3]) {:b ?}}[:vec [[:join [:deco [:fn :a :ok] [[:with [3]]] :ok] [:vec [[:fn :b :ok]] :ok] :ok]] :ok]
    '[{:a ?} ?x]            [:named [:seq [:vec [[:fn :a :ok]] :ok] :ok] '?x :ok]
    '[{:a [{:b ?}]}]        [:seq [:vec [[:join [:fn :a :ok] [:seq [:vec [[:fn :b :ok]] :ok] :ok] :ok]] :ok] :ok]
    '[{:a ?} ?a :seq [1 2]] [:named [:deco [:seq [:vec [[:fn :a :ok]] :ok] :ok] [[:seq [1 2]]] :ok] '?a :ok]
    ))

(deftest valid-proc-keys?
  (testing "post-processors respects schemas"
    (are [exp k] (= exp (sut/valid-proc-keys? k))
      ;; valid options
      true (list :a :when odd?)
      true (list :a :not-found 0 :when even? :with [2])
      true (list :a :with [1 2 3])
      true (list :a :batch [[3] [{:ok 1}]])
      true (list :a :watch (constantly :watch))
      ;; invalid options
      false (list :a :with 3)
      false (list :a :unknown :ko))))

(deftest pattern-valid?
  (testing "Pattern is wrong so throws error."
    (is (thrown? ExceptionInfo (sut/validate-pattern {:a '? :b []})))
    (is (thrown? ExceptionInfo (sut/validate-pattern '[{:a 3} :with [3]]))))
  (testing "Pattern is valid so returns it."
    (are [p] (= p (sut/validate-pattern p))
    ;;basic patterns
      '{:a ?}
      '{:a ? :b ?}

    ;;filtered
      '{:a ? :b 1}

    ;;filter with a function
      {:a '? :b even?}

    ;;guard clause
      {(list :a :when even?) '?}

    ;;guard with not-found
      {(list :a :when even? :not-found 0) '?}

    ;;with option 
      '{(:a :with [3]) ?a}
      '{(:a :with [{:b 2 :c 3}]) {:b ?}}

    ;;seq query
      '[{:a ?}]

    ;;nested map query 
      '{:a {:b ?}}
      '{:a {:b [{:c ?}]}}

    ;;named variable 
      '{:a ?a}

    ;;named variable join
      '{:a ?x :b {:c ?x}}
      '{:a ?x :b ?x}

    ;;named join 
      '{:a ?a :b ?a}

    ;;capture a sequence 
      '[{:a ? :b ?} ?g]

    ;;seq option 
      '[{:a ? :b ?} ? :seq [2 3]]

    ;;batch option 
      '{(:a :batch [[3] [{:ok 1}]]) ?a})))
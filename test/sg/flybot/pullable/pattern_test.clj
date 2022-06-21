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

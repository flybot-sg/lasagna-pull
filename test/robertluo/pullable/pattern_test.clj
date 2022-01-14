(ns robertluo.pullable.pattern-test
  (:require
   [robertluo.pullable.pattern :as sut]
   [clojure.test :refer [deftest are]]))

(deftest ->query
  (are [m exp] (= exp (sut/->query identity m))
    '{:a ?}                 [:vec [[:fn :a]]]
    '{:a ? :b ?}            [:vec [[:fn :a] [:fn :b]]]
    '{:a ?a}                [:vec [[:named [:fn :a] '?a]]]
    '{:a {:b ?}}            [:vec [[:fn :a :a [:vec [[:fn :b]]]]]]
    '{:b 2}                 [:vec [[:filter [:fn :b] 2]]]
    '{:b (?b :not-found 3)} [:vec [[:named [:deco [:fn :b] [[:not-found 3]]] '?b]]]
    '[{:a ?} ?x]            [:named [:seq [:vec [[:fn :a]]]] '?x]
    '[{:a [{:b ?}]}]        [:seq [:vec [[:fn :a :a [:seq [:vec [[:fn :b]]]]]]]]))

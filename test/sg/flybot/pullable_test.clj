(ns sg.flybot.pullable-test
  (:require
   [sg.flybot.pullable :as sut]
   [clojure.test :refer [deftest are testing is]]))

(deftest query
  (testing "`query` can precompile and run"
    (is (= [{:a 1} nil] ((sut/query '{:a ?}) {:a 1})))))

(deftest ^:integrated run
  (are [data x exp] (= exp (sut/run x data))
    ;;basic patterns
    {:a 1}           '{:a ?}        [{:a 1} nil]
    {:a 1 :b 2 :c 3} '{:a ? :b ?}   [{:a 1 :b 2} nil]

    ;;filtered
    {:a 1 :b 2}      '{:a ? :b 1}   [nil nil]

    ;;filter with a function
    {:a 8 :b 2}      {:a '? :b even?} [{:a 8} nil]

    ;;guard clause
    {:a 2}           {(list :a :when even?) '?}  [{:a 2} nil]

    ;;guard with not-found
    {:a 1}           {(list :a :when even? :not-found 0) '?}  [{:a 0} nil]

    ;;with option
    {:a inc}
    '{(:a :with [3]) ?a}
    [{:a 4} {'?a 4}]

    {:a identity}
    '{(:a :with [{:b 2 :c 3}]) {:b ?}}
    [{:a {:b 2}} nil]

    ;;seq query
    [{:a 1} {:a 2 :b 2} {}]
    '[{:a ?}]
    [[{:a 1} {:a 2} {}] nil]

    ;;nested map query
    {:a {:b 1 :c 2}}
    '{:a {:b ?}}
    [{:a {:b 1}} nil]

    {:a {:b [{:c 1 :d 5} {:c 2}]}}
    '{:a {:b [{:c ?}]}}
    [{:a {:b [{:c 1} {:c 2}]}} nil]

    ;;named variable
    {:a 1 :b 2}
    '{:a ?a}
    [{:a 1} {'?a 1}]

    ;;named variable join
    {:a 2 :b {:c 2}}      '{:a ?x :b {:c ?x}} [{:a 2 :b {:c 2}} '{?x 2}]
    {:a 2 :b 1} '{:a ?x :b ?x} [nil {}]      


    ;;named join
    {:a 1 :b 1}
    '{:a ?a :b ?a}
    [{:a 1 :b 1} {'?a 1}]

    ;;capture a sequence
    [{:a 1 :b 2} {:a 3 :b 4 :c 5} {:b 6}]
    '[{:a ? :b ?} ?g]
    [[{:a 1 :b 2} {:a 3 :b 4} {:b 6}]
     {'?g [{:a 1 :b 2} {:a 3 :b 4} {:b 6}]}]

    ;;seq option
    (for [x (range 10)]
      {:a x :b x})
    '[{:a ? :b ?} ? :seq [2 3]]
    [[{:a 2 :b 2} {:a 3 :b 3} {:a 4 :b 4}] nil]

    ;;batch option
    {:a identity}
    '{(:a :batch [[3] [{:ok 1}]]) ?a}
    [{:a [3 {:ok 1}]} {'?a [3 {:ok 1}]}]
    ))

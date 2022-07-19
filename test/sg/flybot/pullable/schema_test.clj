(ns sg.flybot.pullable.schema-test
  (:require
   [sg.flybot.pullable.schema :as sut]
   [clojure.test :refer [deftest are testing]]))


(deftest pattern-valid?
  (testing "Pattern is valid so returns it."
    (are [p] (true? ((sut/pattern-validator nil) p))
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
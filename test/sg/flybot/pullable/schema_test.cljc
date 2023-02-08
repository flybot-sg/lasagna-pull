(ns sg.flybot.pullable.schema-test
  (:require [clojure.test :refer [are deftest testing]] 
            [malli.dev.pretty :as mp] 
            [sg.flybot.pullable.schema :as sut]))

(deftest general-pattern-validator
  (testing "General pattern is valid."
    (are [p] (nil? (-> [:map-of :any :any] (sut/schema-of) (mp/explain p)))
      ;;basic patterns
      '{:a ?}
      '{:a ? :b ?}

      ;;filtered
      '{:a ? :b 1}

      ;;filter with a function
      {:a '? :b even?}

      ;;guard clause
      {:a ('? :when even?)}

      ;;guard with not-found
      {:a (list '? :a :when even? :not-found 0)}

      ;;with option
      '{:a (?a :with [3])}

      ;;seq query
      ;;'[{:a ?}]
      
      ;;nested map query
      '{:a {:b ?}}
      ;; '{:a {:b [{:c ?}]}}
      
      ;; ;;named variable
      ;; '{:a ?a}
      
      ;; ;;named variable join
      ;; '{:a ?x :b {:c ?x}}
      ;; '{:a ?x :b ?x}
      
      ;; ;;named join
      ;; '{:a ?a :b ?a}
      
      ;; ;;capture a sequence
      ;; '[{:a ? :b ?} ?g]
      
      ;; ;;seq option
      ;; '[{:a ? :b ?} ? :seq [2 3]]
      
      ;; ;;batch option
      ;; '{(:a :batch [[3] [{:ok 1}]]) ?a}
      )))

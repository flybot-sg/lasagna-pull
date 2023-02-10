(ns sg.flybot.pullable.schema-test
  (:require [clojure.test :refer [is are deftest testing]]
            [malli.core :as m]
            [malli.dev.pretty :as mp]
            [sg.flybot.pullable.schema :as sut]))

(deftest general-pattern-validator
  (testing "General pattern is valid."
    (are [p] (nil? (-> (sut/pattern-schema-of) (mp/explain p)))
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
      '{(:a :batch [[3] [{:ok 1}]]) ?a}
      )))

(deftest type-based-schema
  (testing "type matches, should all pass!"
    (let [sch [:map [:a :int] [:b :string] [:c [:> 5]]]]
      (are [p] (nil? (-> (sut/pattern-schema-of sch) (mp/explain p)))
        '{:a ?}
        '{:a 5 :b "hello" :c ?x} ;correct types and filters
        '{(:c :not-found 10) ?} ;option
        )))
  (testing "data schema is quite precise, these should fail"
    (let [sch [:map [:a :int] [:b :string] [:c [:> 5]]]]
      (are [p] ((complement nil?) (-> (sut/pattern-schema-of sch) (m/explain p)))
        '{:d ?} ;closed schema
        '{:a ? :b 3 :c ?x} ;wrong type
        '{(:c :default 0) ?a} ;wrong default value
        )))
  (testing "recursive schema"
    (let [sch [:map [:a [:map [:b :int]]]]]
      (is (nil? (-> (sut/pattern-schema-of sch) (mp/explain '{:a {:b ?}}))))
      (is ((complement nil?) (-> (sut/pattern-schema-of sch) (m/explain '{:a {:b :ok}}))))))
  (testing "seq option"
    (let [sch [:map [:a [:vector :int]]]]
      (is (nil? (-> (sut/pattern-schema-of sch) (mp/explain '{(:a :seq [1 5]) ?}))))))
  (testing "seq of maps"
    (let [sch [:sequential [:map [:a :int] [:b :string]]]]
      (are [p] (nil? (-> (sut/pattern-schema-of sch) (mp/explain p)))
        '[{:a ?}]
        '[{:a ?x} ?]
        '[{:b "hello"} ?b :seq [1 5]])))
  )
(ns sg.flybot.pullable.schema-test
  (:require [clojure.test :refer [is are deftest testing]]
            [malli.util :as mu]
            [sg.flybot.pullable.schema :as sut]))


(deftest pattern-validator
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
      '{(:a :batch [[3] [{:ok 1}]]) ?a}))
      (testing "data-schema provided conbined with pattern is valid."
        (is (true? ((sut/pattern-validator
                     [:schema
                      {:registry {::test :int}}
                      [:map [:a :int] [:b :string]]])
                    {:a '?a :b "ok"})))))

(deftest combine-data-pattern
  (testing "Combines the data schema with the pattern registry."
    (are [sch-data sch-exp] (mu/equals
                             [:schema {:registry sut/general-pattern-registry} sch-exp]
                             (sut/combine-data-pattern [:schema nil sch-data]))
      ;; simple map
      [:map [:a :int] [:b :string]]
      [:map [:a [:or ::sut/var :int]] [:b [:or ::sut/var :string]]]

      ;; conserve malli option map
      [:map {:description "ok" :closed true} [:a {:min 1} :int]]
      [:map {:description "ok" :closed true} [:a {:min 1} [:or ::sut/var :int]]]

      ;; nested map
      [:map [:a [:map [:b :string]]]]
      [:map [:a [:map [:b [:or ::sut/var :string]]]]]

      ;; simple seq - not seq of pattern
      [:map [:a [:sequential :int]]]
      [:map [:a [:or ::sut/var [:sequential :int]]]]

      ;; simple seq
      [:map [:a [:sequential [:map [:b :int]]]]]
      [:map [:a [:sequential [:map [:b [:or ::sut/var :int]]]]]]

      ;; nested seq
      [:vector
       [:map
        [:a [:set [:map [:b :keyword]]]]]]
      [:vector
       [:map
        [:a [:set [:map [:b [:or ::sut/var :keyword]]]]]]])))
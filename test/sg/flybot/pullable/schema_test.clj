(ns sg.flybot.pullable.schema-test
  (:require [clojure.test :refer [is are deftest testing]]
            [malli.core :as m]
            [malli.util :as mu] 
            [sg.flybot.pullable.schema :as sut]))

(deftest to-vector-syntax
  (testing "Normal vector to schema vector."
    (are [syntax] (mu/equals [:schema nil :int]
                             (sut/to-vector-syntax syntax))
      ;; vector syntax 
      [:schema :int]
      ;; schema vector syntax
      (m/schema [:schema :int])
      ;; schema map syntax
      {:type :int})))

(deftest combine-data-pattern
  (testing "Combines the data schema with the pattern registry."
    (are [sch-data sch-exp] (mu/equals
                             [:schema {:registry sut/general-pattern-registry} sch-exp]
                             (sut/combine-data-pattern
                              [:schema {:registry sut/general-pattern-registry} sch-data]))
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

      ;; nested seq - not seq of pattern
      [:map [:a [:sequential [:set [:vector :int]]]]]
      [:map [:a [:or ::sut/var [:sequential [:set [:vector :int]]]]]]

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

(deftest pattern-validator
  (testing "No data provided case: pattern is valid."
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

      (testing "data + pattern are valid."
        (is (true? ((sut/pattern-validator
                     [:schema 
                      {:registry {::test :string}}
                      [:map [:a :int] [:b ::test]]])
                    {:a '?a :b "ok"}))))
      (testing "data valid but pattern invalid."
        (is (false? ((sut/pattern-validator
                     [:schema
                      {:registry {::test :string}}
                      [:map [:a :int] [:b ::test]]])
                    {:c '?c}))))
      (testing "pattern valid but data invalid."
        (is (false? ((sut/pattern-validator
                      [:map [:a :int] [:b :int]])
                    {:a '?a :b "ok"}))))
      ;;TODO: data-schema does not work with options (:with, :seq etc)
      )
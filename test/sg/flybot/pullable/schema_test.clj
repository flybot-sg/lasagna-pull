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

(deftest normalize-properties
  (testing "Add nil where the properties are missing."
    (is (= [:map nil [:a nil :int]]
           (sut/normalize-properties [:map [:a :int]])))))

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
      [:map [:a [:or
                 [:sequential [:map [:b [:or ::sut/var :int]]]]
                 [:cat [:map [:b [:or ::sut/var :int]]] ::sut/seq-args]]]]

      ;; nested seq
      [:vector
       [:map
        [:a [:set [:map [:b :keyword]]]]]]
      [:or
       [:vector
        [:map [:a [:or
                   [:set [:map [:b [:or ::sut/var :keyword]]]]
                   [:cat [:map [:b [:or ::sut/var :keyword]]] ::sut/seq-args]]]]]
       [:cat [:map [:a [:or
                        [:set [:map [:b [:or ::sut/var :keyword]]]]
                        [:cat [:map [:b [:or ::sut/var :keyword]]] ::sut/seq-args]]]] 
        ::sut/seq-args]])))

(deftest general-pattern-validator
  (testing "General pattern is valid."
    (are [p] (true? (sut/general-pattern-validator p))
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

(deftest client-pattern-schena
  (testing "The client registry is properly merged with the pattern registry"
    (let [data-schema [:schema
                       {:registry {::test :string}}
                       :int]
          exp         [:schema
                       {:registry (merge {::test :string} sut/general-pattern-registry)}
                       :int]]
      (is (mu/equals exp (sut/client-pattern-schema data-schema))))))

(deftest client-pattern-validator
  (testing "keys are properly encoded for data validation."
    (let [data-schema [:map [:a :int] [:b :string]]]
      (is (true? ((sut/client-pattern-validator data-schema)
                  {'(:a :not-found 0) '?a :b "ok"}))))) 
  (testing "seq option is properly validated."
    (let [data-schema [:vector [:map [:a :int] [:b :int]]]]
      (is (true? ((sut/client-pattern-validator data-schema)
                    '[{:a ? :b ?}])))
      (is (true? ((sut/client-pattern-validator data-schema)
                    '[{:a ? :b ?} ? :seq [2 3]]))))))

(deftest pattern-validator
  (let [data-schema [:schema
                     {:registry {::test :string}}
                     [:map [:a :int] [:b ::test]]]]
    (testing "No data provided case: general pattern is valid."
      (is (= '{:a ?}
             ((sut/pattern-validator nil) '{:a ?}))))
    (testing "data + pattern are valid."
      (is (= {:a '?a :b "ok"}
             ((sut/pattern-validator data-schema) {:a '?a :b "ok"}))))
    (testing "data valid but pattern invalid."
      (is (= :general-pattern-syntax
           (-> {'(:a :wrong-option [3]) '?a} ((sut/pattern-validator data-schema)) :error :type))))
    (testing "pattern valid but data invalid."
      (is (= :client-pattern-data
           (-> {:a '?a :b :ko} ((sut/pattern-validator data-schema)) :error :type))))))
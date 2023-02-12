; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.schema
  "Pattern validation with Malli schema."
  (:require [malli.core :as m]
            [malli.util :as mu]))
 
(defn lvar?
  "predict if `x` is a logical var, a.k.a lvar"
  [x]
  (boolean (and (symbol? x) (re-matches #"\?.*" (str x)))))

^:rct/test
(comment
  (lvar? '?) ;=> true
  (lvar? '?x) ;=> true
  (lvar? 'other) ;=> false
  )

(let [types #{:vector :sequential :set (m/type vector?) (m/type set?)}]
  (defn- seq-type?
    "predict `x` type is a sequential type"
    [t]
    (boolean (types t))))

(let [types #{:fn :function :=> (m/type fn?) :any}]
  (defn- func-type?
    "predict `x` type is a function type"
    [t]
    (boolean (types t))))

(defn- mark-ptn
  "mark a malli `schema` is a pullable pattern"
  [schema]
  (mu/update-properties schema assoc ::pattern? true))

(defn- ptn? 
  "predict if a malli schema is a pullable pattern"
  [schema]
  (-> (m/properties schema) ::pattern?))

(defn- options-of
  "returns pullable pattern option for `schema`"
  [schema]
  [:cat
   [:*
    (into
     [:alt]
     (let [t (m/type schema)]
       (cond-> [[:cat [:= :default] schema]
                [:cat [:= :not-found] schema]
                [:cat [:= :when] fn?]]
         (func-type? t)
         (into [[:cat [:= :with] vector?]
                [:cat [:= :batch] [:vector vector?]]])
         (or (= :any t) (seq-type? t))
         (into [[:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]))))]])

^:rct/test
(comment
  (def try-val #(-> % (options-of) (m/explain %2)))
  (try-val :int [:default 5]) ;=> nil
  (try-val :int [:default :ok]) ;=>> (complement nil?)  
  (try-val fn? [:with [3]]) ;=> nil 
  )

(defn- entries-collector
  "collect information on entries of a malli map schema returns a pair:
   entries, option schema map: key to schema explainer"
  [entries]
  (reduce 
   (fn [rslt [key props child]]
     (-> rslt
         (update 0 conj [key 
                         (assoc props :optional true)
                         [:or child [:fn lvar?] [:and fn? [:=> [:cat child] :boolean]]]])
         (update 1 conj (->> [key (when-not (ptn? child) (options-of child))]))))
   [[] {}] entries))

^:rct/test
(comment
  (entries-collector [[:a {} :int] [:b {} :keyword]]) ;=>> [[[:a {:optional true} ...] [:b {:optional true} ...]] {}]
  )

(defn- map-extractor
  "extract a map `m` returns a map pair if a key in the map is a list:
    1. a map with only the 1st item of the key to the values
    2. a map with the 1st key to the rest of the list"
  [m]
  (reduce 
   (fn [rslt [k v]]
     (let [[key rest] ((juxt first rest) (if (list? k) k (list k)))]
       (cond-> (update rslt 0 conj [key v])
         (seq rest) (update 1 conj [key rest]))))
   [{} {}] m))

^:rct/test
(comment
  (map-extractor {:a :int '(:b :default 0) :int}) ;=> [{:a :int, :b :int} {:b [:default 0]}]
  )

(defn- options-explainer
  "explain options according to options-map"
  [options-map path continue?]
  (let [reporter (fn [& args] (zipmap [:path :in :value :type] args))]
    (fn [options in acc]
      (reduce 
       (fn [acc [key option]]
         (if-let [schema (m/schema (get options-map key))]
           (if-let [errors ((m/explainer schema path) option in acc)]
             (cond-> (conj acc (reporter path in errors ::m/invalid))
               continue? (reduced))
             acc)
           (conj (reporter path in [key option] ::m/invalid))))
      acc options))))

^:rct/test
(comment
  (def explainer (options-explainer {:a [:cat [:= :default] :int]} [] true))
  (explainer {:a '[:default 5]} [:a] []) ;=> []
  )

(defn- pattern-map-schema
  "delegated schema"
  [map-schema options-map]
  (reify m/IntoSchema
    (-type [_] :pattern-map)
    (-into-schema
      [this properties children options]
      ^{:type ::m/schema}
      (reify m/Schema
        (-properties [_] properties)
        (-form [_] [:pattern-map (m/-form map-schema) options-map])
        (-parent [_] this)
        (-options [_] options)
        (-children [_] children)

        (-walk [_ p1 p2 p3]  (m/-walk map-schema p1 p2 p3))
        (-validator
          [_]
          (fn [m]
            (let [[stripped options] (map-extractor m)]
              ((m/-validator map-schema) stripped))))
        (-explainer
         [_ path] 
         (fn [m in msgs]
           (let [[stripped options] (map-extractor m)
                 map-errors ((m/-explainer map-schema path) stripped in msgs)
                 option-explainer (options-explainer options-map path true)]
             (option-explainer options in map-errors))))))))

;;-------------------------------
; Public

(defn pattern-schema-of 
  "returns a pattern schema for given `data-schema`, default to general map or
   sequence of general maps."
  ([]
   (pattern-schema-of nil))
  ([data-schema]
   (m/walk
    (or data-schema [:or
                     [:map-of :any :any]
                     [:sequential [:map-of :any :any]]])
    (m/schema-walker
     (fn [sch]
       (let [t (m/type sch)]
         (cond
           (= :map t)
           (let [[new-children options-map] (entries-collector (m/children sch))]
             (-> sch
                 (mu/transform-entries (constantly new-children))
                 (mu/update-properties assoc :closed true)
                 (pattern-map-schema options-map)
                 (mark-ptn)))

           (= :map-of t)
           (-> sch
               (mu/transform-entries
                (fn [[key-type val-type]]
                  (let [vector-ptn [:or val-type [:fn lvar?] (options-of val-type)]]
                    [key-type vector-ptn])))
               (mark-ptn))

           (and (seq-type? t) (seq (m/children sch)))
           (let [x (-> sch m/children first)]
             (if (ptn? x)
               (-> [:cat 
                    x 
                    [:? [:fn lvar?]] 
                    [:? [:alt [:cat [:= :seq] [:vector {:min 1 :max 2} :int]]]]]
                   (mark-ptn))
               sch))

           :else sch)))))))

^:rct/test
(comment
  (def ptn-schema (pattern-schema-of [:map [:a :int]]))
  (m/explain ptn-schema '{:a ?}) ;=> nil
  (m/explain ptn-schema '{(:a) ?}) ;=> nil
  (m/explain ptn-schema '{(:a :default 0) ?});=> nil
  (m/explain ptn-schema '{(:a default :ok) ?}) ;=>> (complement nil?)

  (def ptn-schema2 (pattern-schema-of [:map [:a [:map [:b :int]]]]))
  (m/explain ptn-schema2 '{:a {:b :ok}}) ;=>> (complement nil?)
  (m/explain ptn-schema2 '{:a {(:b :default 0) ?}}) ;=> nil

  (def ptn-schema3 (pattern-schema-of [:map [:b fn?]]))
  (m/explain ptn-schema3 {(list :b :default identity) '?})

  )
(ns robertluo.pullable.option
  (:require
   [clojure.string :as str]
   [robertluo.pullable.query :as core])
  (:import
   [clojure.lang ExceptionInfo]))

;;====================================
;; Options are wrapper of another query

(defn camel-case [^String s]
  (->> (.split s "-") (map str/capitalize) (apply str)))

(defn pattern-error [msg arg]
  (ex-info msg (assoc {:error/kind :pattern} :arg arg)))

(defmacro def-query-option
  "Define an option with option-name (must be a keyword), an argument.
   When define an option, implicit binding `this` and `query` can be used.
   An option can have optional methods:
    - `key` the key of result
    - `value-of` the value of the result, binding `m` means the data source.
    - `transform` transforming `target` with `m` source
    - `assert-args` asserts if argument provided is legal"
  [option-name arg & {:syms [key value-of transform assert-arg]}]
  (let [type-name (-> option-name (name) (camel-case) (str "Option") (symbol))]
    `(do
       (defrecord ~type-name [~'query ~arg]
         core/Query
         (-key [~'this]
           ~(or `~key `(core/-key ~'query)))
         (-value-of [~'this ~'m]
           ~(or `~value-of `(core/-value-of ~'query ~'m)))
         (-transform [~'this ~'target ~'m]
           ~(or `~transform  `(core/default-transform ~'this ~'target ~'m))))

       (defmethod core/create-option ~option-name
         [{:option/keys [~'arg ~'query]}]
         ~@(when assert-arg
             `(do
               (when-not (~assert-arg ~'arg)
                 (throw (pattern-error "Option error" ~'arg)))))
         (new ~type-name ~'query ~'arg)))))

;;========================
;; options implementation

(def-query-option :as k
  key [k])

(def-query-option :not-found not-found 
  value-of
  (let [v (core/-value-of query m)]
    (if (= v ::core/none)
      not-found
      v)))

(def-query-option :exception ex-handler
  value-of
  (try
    (core/-value-of query m)
    (catch ExceptionInfo ex
      (ex-handler ex)))
  assert-arg fn?)

(defn value-error [msg v]
  (ex-info msg (assoc {:error/kind :value} :value v)))

(def-query-option :seq off-limit
  value-of
  (let [v (core/-value-of query m)
        [offset limit] off-limit]
    (if (seqable? v)
      (cond->> v
        offset (drop offset)
        limit (take limit))
      (throw (value-error "value not seqable" v)))))

(def-query-option :with args
  value-of
  (let [v (core/-value-of query m)]
    (if (fn? v)
      (apply v args)
      (throw (value-error "value is not a function" v)))))

; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.util
  "misc utility functions"
  (:require [clojure.walk :as walk]))

(defrecord DataError [data query-id reason])

(defn data-error
  "returns an exception represent data is not as expected
   - `data`: the data been queries
   - `qid`: the query id being running
   - `reason`: a string general description"
  ([data qid]
   (data-error data qid "data error"))
  ([data qid reason]
   (DataError. data qid reason)))

(defn error?
  "predict if `x` is error"
  [x]
  (instance? DataError x))

^:rct/test
(comment
  (data-error {} :k) ;=>> {:query-id :k}
  (error? (data-error {} :k))) ;=> true


(defn named-lvar?
  "predict if `x` is a named logical value"
  [x]
  (and (symbol? x) (re-matches #"\?\S+" (name x))))

(defn named-lvars-in-pattern
  "returns named lvars in `pattern`"
  [pattern]
  (let [rslt (transient #{'&?})
        collector (fn [x] (when (named-lvar? x) (conj! rslt x)) x)]
    (walk/postwalk collector pattern)
    (persistent! rslt)))

^:rct/test
(comment
  (named-lvars-in-pattern '{:a ? :b ?b :c ?c})) ;=> #{?b ?c &?}



^:rct/test
(comment
  (named-lvar? 3) ;=> false
  (named-lvar? '?a)) ;=>> some?


;; To avoid hard dependency of malli.
;; But this not work with ClojureScript.
;; For Cljs developers, if want to use schema,
;; has to require sg.flybot.pullable.schema manually.

#?(:clj
   (defmacro optional-require
     "optionally try requre `require-clause`, if success, run `if-body`,
   else `else-body`"
     [require-clause if-body else-body]
     (if
      (try
        (requiring-resolve require-clause)
        true
        (catch Exception _
          false))
      if-body
      else-body)))

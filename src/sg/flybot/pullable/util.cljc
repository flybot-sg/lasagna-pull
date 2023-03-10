; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns sg.flybot.pullable.util
  "misc utility functions")

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
  (error? (data-error {} :k)) ;=> true
  )

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
        (require require-clause)
        true
        (catch Exception _
          false))
       if-body
       else-body)))

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

(comment
  (pr-str
   (data-error {} :k))
  )

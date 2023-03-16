; Copyright 2022, Flybot Pte. Ltd.
; Apache License 2.0, http://www.apache.org/licenses/

(ns ^:no-doc sg.flybot.pullable.core.option
  "support for decorate query options"
  (:require [sg.flybot.pullable.util :as u :refer [data-error]]))

(defmulti apply-post
  "create a post processor by ::pp-type, returns a function takes
   k-v pair, returns the same shape of data"
  :proc/type)

(defn assert-arg!
  "An error that represent apply-post illegal argument."
  [pred arg]
  (when-not (pred (:proc/val arg))
    (throw (ex-info "illegal argument" arg))))

(defmethod apply-post :default
  [arg]
  (assert-arg! (constantly false) arg))

^:rct/test
(comment
  (apply-post {:proc/type :default}) ;throws=>> {:error/class clojure.lang.ExceptionInfo}
  )

;;### :when option
;; Takes a pred function as its argument (:proc/val)
;; when the return value not fullfil `pred`, it is not included in result.
(defmethod apply-post :when
  [arg]
  (let [{pred :proc/val} arg]
    (assert-arg! fn? arg)
    (fn [[k v]]
      [k (when (pred v) v)])))

^:rct/test
(comment
  ((apply-post {:proc/type :when :proc/val 1}) [:a 1]) ; throws=>> {:error/class clojure.lang.ExceptionInfo}
  ;;when everything ok, it returns the original data
  ((apply-post {:proc/type :when :proc/val odd?}) [:a 1]) ;=> [:a 1]
  ((apply-post {:proc/type :when :proc/val odd?}) [:a 0]) ;=> [:a nil]
  )

;;### :not-found option
;; Takes any value as its argument (:proc/val)
;; When a value not found, it gets replaced by not-found value
(defmethod apply-post :not-found
  [{:proc/keys [val]}]
  (fn [[k v]]
    [k (or v val)])) 

^:rct/test
(comment
  ((apply-post {:proc/type :not-found :proc/val ::default}) [:a 1]) ;=> [:a 1]
  ((apply-post {:proc/type :not-found :proc/val :default}) [:a nil]) ;=> [:a :default]
  )

;;### :with option
;; Takes a vector of args as this option's argument (:proc/val)
;; Requires value being a function, it applies the vector of args to it,
;; returns the return value as query result.
(defmethod apply-post :with
  [arg]
  (let [{args :proc/val} arg]
    (assert-arg! vector? arg)
    (fn [[k f]]
      [k (if (fn? f)
           (apply f args)
           (data-error f k "value must be a function"))])))

^:rct/test
(comment
  ((apply-post {:proc/type :with :proc/val 3}) [:a 1]) ;throws=>> {:error/class clojure.lang.ExceptionInfo}
  ((apply-post {:proc/type :with :proc/val [3]}) [:a 3]) ;=>> [:a u/error?]
  ((apply-post {:proc/type :with :proc/val [3]}) [:a inc]) ;=> [:a 4]
  )

;;### :batch option
;; Takes a vector of vector of args as this options's argument. 
;; Applible only for function value.
;; query result will have a value of a vector of applying resturn value.
(defmethod apply-post :batch
  [arg]
  (assert-arg! #(and (vector? %) (every? vector? %)) arg)
  (let [{args-vec :proc/val} arg]
    (fn [[k f]]
      [k (if-not (fn? f)
           (data-error f k "value must be a function")
           (map #(apply f %) args-vec))])))

^:rct/test
(comment
  ((apply-post {:proc/type :batch :proc/val [3]}) [:a inc]) ;throws=>> {:error/class clojure.lang.ExceptionInfo}
  ((apply-post {:proc/type :batch :proc/val [[3] [4]]}) [:a inc]) ;=> [:a [4 5]]
  ((apply-post {:proc/type :batch :proc/val [[3] [4]]}) [:a 3]) ;=>> [:a u/error?]
  )

;;### :seq option (Pagination)
;; Takes a pair of numbers as this option's argument.
;;  [:catn [:from :number] [:count :number]]
;; Appliable only for seq query.
;; query result has a value of a sequence of truncated sequence.
(defmethod apply-post :seq
  [arg]
  (assert-arg! vector? arg)
  (let [[from cnt] (:proc/val arg)
        from       (or from 0)
        cnt        (or cnt 0)]
    (fn [[k v]]
      [k (if-not (seqable? v)
           (data-error v k "seq option can only be used on sequences")
           (->> v (drop from) (take cnt)))])))

^:rct/test
(comment
  ((apply-post {:proc/type :seq :proc/val 3}) [:a inc]) ;throws=>> {:error/message #"illegal argument"}
  ((apply-post {:proc/type :seq :proc/val [1 3]}) [:a (range 8)]) ;=> [:a [1 2 3]]
  ((apply-post {:proc/type :seq :proc/val [1 3]}) [:a 1]) ;=>> [:a u/error?]
  )

;;### :watch option
;; Takes an function as the argument (:proc/val): 
;;    [:=> [:catn [:old-value :any] [:new-value :any]] :any]
;; returns `nil` when your do want to watch it anymore.
;; Can watch on a IRef value
(def watchable?
  "pred if `x` is watchable"
  (fn [x]
    #?(:clj  (instance? clojure.lang.IRef x)
       :cljs (satisfies? IDeref x))))

(defmethod apply-post :watch
  [arg]
  (assert-arg! fn? arg)
  (let [f       (:proc/val arg)
        w-k     ::watch
        watcher (fn [_ watched old-value new-value]
                  (when (nil? (f old-value new-value))
                    (remove-watch watched w-k)))]
    (fn [[k v]]
      (if (watchable? v)
        (do (add-watch v w-k watcher)
            [k @v])
        [k (data-error v k "watch option can only apply to an watchable value")]))))

^:rct/test
(comment
  ((apply-post {:proc/type :watch :proc/val 1}) [:a inc]) ;throws=>> {:error/class clojure.lang.ExceptionInfo}
  ((apply-post {:proc/type :watch :proc/val identity}) [:a inc]) ;=>> [:a u/error?]
  (def a (atom 0))
  (def b (atom 0))
  ((apply-post {:proc/type :watch :proc/val (fn [_ v] (reset! b v))}) [:a a]);=> [:a 0]
  (reset! a 5)
  @b ;=> 5
  )
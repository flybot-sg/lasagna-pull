(ns sg.flybot.pullable.parameter
  "Supporting query pattern parameterization.")

;; ## Data structure - FPP (aka function/parameter-names pair)
;; A predict supporting parameters is a function with vector of names of parameters.

(defn fpp?
  "predict if `x` is a FPP"
  [x]
  (and (vector? x) (fn? (first x)) (vector? (second x))))

(defn pred-of-fpp
  "returns a pred function which can be called using `parameters` table"
  [f-param-value [f paramter-names]]
  (fn [v]
    (let [parameter-val (mapv f-param-value paramter-names)]
      (apply f (conj parameter-val v)))))

(defn parameter-symbol?
  "predict if `x` is parameter symbol, e.g. symbol starts with *"
  [x]
  (and (symbol? x) (.startsWith (name x) "*")))

(defn func-vec
  "returns a FPP of a symbol"
  [sym]
  [#(= % %2) [sym]])

(defn pred-of
  [f-param-value x]
  (cond
    (fpp? x)              (pred-of-fpp f-param-value x)
    (parameter-symbol? x) (pred-of-fpp f-param-value (func-vec x))
    (fn? x)               x
    :else                 #(= % x)))

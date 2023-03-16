(ns hooks.qfn
  (:require [clj-kondo.hooks-api :as api]
            [clojure.walk :as walk]))

;; Hook definition according to
;; https://github.com/clj-kondo/clj-kondo/blob/master/doc/hooks.md

;; copied from sg.flybot.pullable.util
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

(defn qfn [{:keys [node]}]
  (let [[pattern-node & body] (rest (:children node))]
    (when (not pattern-node)
      (throw (ex-info "No pattern provided" {})))
    (let [syms (->> (named-lvars-in-pattern (api/sexpr pattern-node)) 
                    (map api/token-node)
                    (api/vector-node)) 
          new-node (api/list-node
                    (list*
                     (api/token-node 'fn)
                     (api/vector-node
                      [(api/map-node [(api/keyword-node :syms) syms])])
                     ;disable '&? not used warning
                     (api/list-node
                      [(api/token-node 'comment)
                       (api/token-node '&?)])
                     body))]
      {:node new-node})))

(comment
  (def code1 "(fn [{:syms [?a]}] (+ ?a))")
  (def co "(require '[hooks.qfn])(hooks.qfn/qfn '{:a ?a} (+ ?a))")
  (def code "(require '[sg.flybot.pullable :as p]) (p/qfn '{:a ?a} '?a)")

  (-> (api/parse-string code1) api/sexpr)
  (-> (qfn {:node (api/parse-string "(qfn '{:a ?a} (+ ?a))")})
      (:node)
      (api/sexpr))
  )
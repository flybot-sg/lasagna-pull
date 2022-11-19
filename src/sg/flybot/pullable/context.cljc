(ns sg.flybot.pullable.context
  (:require
   [sg.flybot.pullable.context.executor :as executor]))

(defprotocol QueryContext
  "A shared context between subqueries"
  (-create-query [context query-type args]
    "returns a query of keyword `query-type` and optional `args` vector")
  (-finalize [context m] "returns a map when queries are done on map `m`"))

(defn stateless-context
  "returns a stateless context"
  [query-ctor]
  (reify QueryContext
    (-create-query [_ query-type args]
      (query-ctor query-type args))
    (-finalize [_ m]
      m)))

(defn named-query-factory
  "returns a new NamedQueryFactory
   - `a-symbol-table`: an atom of map to accept symbol-> val pair"
  ([query-decorator parent-context]
   (named-query-factory query-decorator parent-context (atom nil)))
  ([query-decorator parent-context a-symbol-table]
   (reify QueryContext
     (-create-query
       [_ query-type args]
       (if (not= :named query-type)
         (-create-query parent-context query-type args)
         (let [[q sym] args]
           (if-not sym
             q
             (query-decorator 
              q
              (fn [id _ v accept]
                (let [old-v    (get @a-symbol-table sym ::not-found)
                      set-val! #(do (swap! a-symbol-table assoc sym %) %)
                      rslt
                      (condp = old-v
                        ::not-found (set-val! v)
                        v           v
                        ::invalid   ::invalid
                        (set-val! ::invalid))]
                  (accept (when (not= rslt ::invalid) id) rslt))))))))
     (-finalize [_ m]
       (if-let [binds @a-symbol-table]
         (into
          m
          (filter (fn [[_ v]] (not= ::invalid v)))
          binds)
         m)))))

(defrecord Coeffect [effect fetch-fn])
(def coeffect
  "returns a co-effect object"
  ->Coeffect)

(defn co-effect?
  [x]
  (instance? Coeffect x))

(defn coeffects-context
  "returns a co-effective context derived from `parent-context`"
  [parent-context query-decorator executor]
  (reify QueryContext
    (-create-query
     [_ query-type args]
     (query-decorator
      (-create-query parent-context query-type args)
      (fn [k v _ accept]
        (if-not (co-effect? v)
          (accept k v)
          (do
            (executor/-receive-effect executor (:effect v))
            (accept k (delay ((:fetch-fn v) (executor/-result executor)))))))))
    (-finalize
      [_ m]
      (do
        (executor/-run! executor)
        (-finalize parent-context m)))))

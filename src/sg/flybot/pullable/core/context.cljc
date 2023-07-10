(ns sg.flybot.pullable.core.context)

(defprotocol QueryContext
  "A shared context between subqueries"
  (-wrap-query [context query args]
    "returns a wrapped query from `query` and optional arguments `args`")
  (-finalize [context m] "returns a map when queries are done on map `m`"))

(defn composite-context
  "returns a composite context which is composed by many contexts"
  [contexts]
  (reify
    QueryContext
    (-wrap-query [_ q args]
      (reduce #(-wrap-query %2 % args) q contexts))
    (-finalize [_ acc]
      (reduce #(-finalize %2 %) acc contexts))))

(extend-protocol QueryContext
  nil
  (-wrap-query [_ q _] q)
  (-finalize [_ m] m))


(ns sg.flybot.pullable.executor
  "Protocols and reference implentations for co-effects executor.
   
   A co-effects collect workloads from queries, postpone the execution
   of them.")

(defprotocol EffectExecutor
  "An executor to accumulate effects and run it in batch (transaction)"
  (-receive-effect [executor workload] "receive `workload`, returns false if fails")
  (-run! [executor] "runs the accumulated workloads")
  (-empty? [executor] "predict if this is pending workload for the executor")
  (-result [executor] "returns the collective running result of the executor"))

(defn accumulative-executor
  "returns an accumunicative executor which collect workload into a vector,
   using `effect-runner` to apply then workloads, and `result-getter`
   to get then running result.
   
    - `effect-runner`: function [workloads] -> new-result-value.
    - `result-getter`: function [] -> result-value." 
  [effect-runner result-getter]
  (let [tasks (atom [])]
    (reify EffectExecutor
      (-receive-effect
        [_ workload]
        (swap! tasks conj workload))
      (-run!
        [_]
        (letfn [(updating
                 [val]
                 (reduce 
                  (fn [acc [f & args]] 
                    (apply f acc args)) val @tasks))]
          (effect-runner updating)
          (reset! tasks [])))
      (-empty? [_] (boolean (seq @tasks)))
      (-result
        [_]
        (result-getter)))))

(defn atom-executor
  "returns an atom exectutor of atom `a-db`"
  [a-db]
  (accumulative-executor #(swap! a-db %) #(deref a-db)))

(comment
  (def ex (atom-executor (atom 0)))
  (do (-receive-effect ex [+ 5])
      (-receive-effect ex [* 8])
      (-run! ex)))

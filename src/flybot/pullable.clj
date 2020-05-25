(ns flybot.pullable
  (:require
   [flybot.pullable.core :as impl]))

(defn pull
  "Returns data from data using pattern ptn."
  [data ptn]
  (when-let [q (impl/pattern->query ptn)]
    (impl/-select q data)))
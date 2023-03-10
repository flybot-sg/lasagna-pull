(ns trail
  (:require [sg.flybot.pullable :as pull]))

(defn -main []
  (pull/run-query '{:a ?} {:a 1}))
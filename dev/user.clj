(ns user
  "just for development")

(try
  (:require '[nextjournal.clerk :as clerk])
  (clerk/serve! {:watch-paths ["notebook" "src"]})
  (catch Exception e 
    (println e)))

(ns user
  "just for development"
  (:require [nextjournal.clerk :as clerk]))

(clerk/serve! {:watch-paths ["notebook" "src"]})

(ns user
  "just for development")

(comment
  (do
    (require '[nextjournal.clerk :as clerk])
    (clerk/serve! {:watch-paths ["notebook" "src"]}))
  )

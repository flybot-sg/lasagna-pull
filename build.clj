(ns build
  "Build script for this project"
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as cb]))

(defn project 
  "apply project default to `opts`"
  [opts]
  (let [defaults {:lib     'sg.flybot/lasagna-pull
                  :version (format "0.4.%s" (b/git-count-revs nil))
                  :scm     {:url "https://github.com/flybot-sg/lasagna-pull"}}]
    (merge defaults opts)))

(defn tests 
  "run all tests, for clj and cljs."
  [opts]
  (-> opts
      (cb/run-task [:dev :test])
      (cb/run-task [:dev :cljs-test])))

(defn copy-clj-kondo-config
  "copy clj-kondo definition to local dev env"
  [opts]
  (let [{:keys [lib] :as opts} (project opts)
        config-dir (str "resources/clj-kondo.exports/" lib)]
    (b/copy-dir {:src-dirs [config-dir]
                 :target-dir ".clj-kondo"})
    opts))

(defn ci
  [opts]
  (-> opts
      (project)
      (cb/clean)
      (tests)
      (cb/jar)))

(defn deploy
  [opts]
  (-> opts
      (project)
      (cb/deploy)))

(ns build
  "Build script for this project"
  (:require [clojure.tools.build.api :as b]
            [org.corfield.build :as cb]))

(defn project
  "apply project default to `opts`"
  [opts]
  (let [version (format "0.4.%s" (b/git-count-revs nil))
        defaults {:lib     'sg.flybot/lasagna-pull
                  :version version}]
    (merge defaults opts)))

(defn pom
  [opts]
  (let [pom-data
        [[:description "Precisely select from deep data structure"]
         [:url "https://github.com/flybot-sg/lasagna-pull"]
         [:licenses
          [:license
           [:name "Apache-2.0"]
           [:url "https://www.apache.org/licenses/LICENSE-2.0.txt"]]]
         [:organization "Flybot Pte Ltd"]]
        opts (merge opts
                    {:basis (b/create-basis {})
                     :target "target"
                     :pom-data pom-data})]
    (b/write-pom opts)
    opts))

(defn tests
  "run all tests, for clj and cljs."
  [opts]
  (-> opts
      (cb/run-task [:dev :test])
      (cb/run-task [:dev :cljs-test])))

(defn ci
  [opts]
  (-> opts
      (project)
      (cb/clean)
      (tests)
      (pom)
      (cb/jar)))

(defn deploy
  [opts]
  (-> opts
      (project)
      (cb/deploy)))

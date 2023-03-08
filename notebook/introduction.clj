(ns introduction 
  (:require [sg.flybot.pullable :as pull]))

;;## Problem

;; Let's see some data copied from deps.edn:

(def data {:deps 
           {:paths ["src"],
            :deps {},
            :aliases
            {:dev
             {:extra-paths ["dev"],
              :extra-deps
              {'metosin/malli {:mvn/version "0.10.2"},
               'com.bhauman/figwheel-main {:mvn/version "0.2.18"},
               }},
             :test
             {:extra-paths ["test"],
              :extra-deps
              {'lambdaisland/kaocha {:mvn/version "1.80.1274"}},
              :main-opts ["-m" "kaocha.runner"]}}}})

;; In `clojure.core`, we have `get`, `get-in` to extract information 
;; from a map and we are very familiar with them. However, if we have multiple 
;; pieces of information need and they are stored in different location of the map, 
;; things are starting getting tricky. 
;;

(let [global-path     (get-in data [:deps :paths])
      dev-extra-path  (get-in data [:deps :aliases :dev :extra-paths])
      test-extra-path (get-in data [:deps :aliases :test :extra-paths])]
  (concat global-path dev-extra-path test-extra-path))

;; We have to call `get`/`get-in` multiple times manually.

;;## Bring Lasagna-pull in

;; Using lasagna-pull, we can do it in a more concise way:

(def pattern '{:deps {:paths   ?global
                      :aliases {:dev  {:extra-paths ?dev}
                                :test {:extra-paths ?test}}}})

;; As you may see above, lasagna-pull using a pattern which mimics your data
;; to match it, a logic variable marks the piece of information we are interested,
;; it is easy to write and easy to understand.

(let [[_ {:syms [?global ?dev ?test]}] ((pull/query pattern) data)]
     (concat ?global ?dev ?test))

;; `pull/match` takes a pattern and match it to data, returns a pair, and
;; the second item of the pair is a map, contains all logical da

;;### Select subset of your data

;; Another frequent situation is selecting subset of data, we could use 
;; `select-keys` to shrink a map, and `map` it over a sequence of maps.
;; Lasagna-pull provide this with nothing to add:

(-> ((pull/query pattern) data) first)

;; Just check the first item of the matching result, it only contains
;; information we asked, retaining the original data shape.

(def person&fruits {:persons [{:name "Alan", :age 20, :sex :male}
                              {:name "Susan", :age 12, :sex :female}]
                    :fruits [{:name "Apple", :in-stock 10}
                             {:name "Orange", :in-stock 0}]})

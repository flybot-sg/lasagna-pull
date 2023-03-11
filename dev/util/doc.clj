(ns util.doc
  "create documentation from notebooks"
  (:require
   [rewrite-clj.zip :as zip]
   [clojure.string :as str]))

(defn comment-doc
  [node]
  (let [{:keys [prefix s]} node]
    (when (and (= ";" prefix) (str/starts-with? s ";"))
      (subs s 1))))

^:rct/test
(comment
  (comment-doc {:s ";hello" :prefix ";"}) ;=> "hello"
  )

(defn code->md
  [zloc]
  (let [eval-rslt (when (zip/sexpr-able? zloc) (eval (zip/sexpr zloc)))
        eval-str (when eval-rslt (format "\n(comment\n;=>\n%s)" (prn-str eval-rslt)))]
    (str "```clojure\n" (zip/string zloc) eval-str "\n```\n")))

^:rct/test
(comment
  (code->md (zip/of-string "3")) ;=> "```clojure\n3\n(comment\n;=>\n3\n)\n```\n"
  )

(defn transform-loc
  [zloc]
  (loop [loc zloc acc []]
    (if (zip/end? loc)
      acc
      (let [[dir elem]
            (case (zip/tag loc)
              :forms [zip/down*]
              :comment [zip/right* (comment-doc (zip/node loc))]
              (:whitespace :newline) [zip/right*]
              [zip/right* (code->md loc)])]
        (recur (dir loc) (if elem (conj acc elem) acc))))))

^:rct/test
(comment
  (transform-loc (zip/of-string ";;ok\n5\n")) ;=> ["```clojure\n5\n(comment\n;=>\n5\n)\n```\n"]
  (transform-loc (zip/of-string ";;hello\n")) ;=> ["hello\n"]
  )

(defn notebook->md
  [{:keys [from to]}]
  (assert (and from to))
  (->> (zip/of-file from) transform-loc (apply str) (spit to)))

(comment
  ;;do the real transformation
  (notebook->md {:from "notebook/introduction.clj" :to "README.md"})
  )
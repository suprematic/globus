(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b] ; for b/git-count-revs
            [org.corfield.build :as bb]))

(def lib 'net.clojars.glob/glob)
#_(def version "0.1.0-SNAPSHOT")
; alternatively, use MAJOR.MINOR.COMMITS:
(def version (format "0.1.%s" (b/git-count-revs nil)))

(defn test "Run the tests." [opts]
  (bb/run-tests opts))

(defn test-node "Run node.js tests" [opts]
  (b/delete {:path "out/node-test.js"})
  (b/process {:command-args ["clj" "-M:shadow-cljs" "compile" "node-test"]})
  (b/process {:command-args ["node" "out/node-test.js"]}))

(defn ci "Run the CI pipeline of tests (and build the JAR)." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/run-tests)
      (bb/clean)
      (bb/jar)))

(defn install "Install the JAR locally." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/install)))

(defn deploy "Deploy the JAR to Clojars." [opts]
  (-> opts
      (assoc :lib lib :version version)
      (bb/deploy)))

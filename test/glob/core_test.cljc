(ns glob.core-test
  (:require [clojure.test :refer :all]
            [glob.core :refer :all]))

(deftest a-test
  (testing "proof of concept"
    (is (= (glob "a*c" ["aaa" "bbb" "abc" "def"]) ["abc"]))))

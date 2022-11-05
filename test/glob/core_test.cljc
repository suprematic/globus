(ns glob.core-test
  (:require
    #?(:clj [clojure.test :refer :all]
       :cljs [cljs.test :refer-macros [deftest testing is]])
    [glob.core :as glob]))

(deftest a-test 
  (testing "proof of concept"
    (is (= (glob/glob "a*c" ["aaa" "bcb" "abc" "def"]) ["abc"]))))

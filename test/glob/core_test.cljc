(ns glob.core-test
  (:require
    #?(:clj [clojure.test :refer :all]
       :cljs [cljs.test :refer-macros [deftest testing is]])
    [glob.core :as glob])
  (:import
    #?(:clj [clojure.lang ExceptionInfo])))

(def ^:private HAYSTACK
  ["aaaa" "abcd" "bbbb" "cdcdcdcd" "abcd-23" "ff"])

(deftest glob-basics 
  (testing "no magic"
    (is (= (glob/glob "aaaa" HAYSTACK) ["aaaa"]))
    (is (= (glob/glob "xxxx" HAYSTACK) [])))
  (testing "star"
    (is (= (count (glob/glob "a*" HAYSTACK)) 3))
    (is (= (glob/glob "*bb*" HAYSTACK) ["bbbb"]))
    (is (= (glob/glob "*xx*" HAYSTACK) [])))
  (testing "question mark"
    (is (= (glob/glob "??" HAYSTACK) ["ff"]))
    (is (= (glob/glob "a?cd" HAYSTACK) ["abcd"])))
  (testing "characater set"
    (is (= (glob/glob "abc[a-f]-2[0-9]" HAYSTACK) ["abcd-23"]))
    (is (= (glob/glob "abc[^a-f]-2[0-9]" HAYSTACK) [])))
  (testing "alternatives"
    (is (= (glob/glob "abc{d,d-23}" HAYSTACK) ["abcd" "abcd-23"]))
    (is (= (glob/glob "abcd{,-23}" HAYSTACK) ["abcd" "abcd-23"])))
  (testing "combos"
    (is (= (glob/glob "[ba][^0-9]*{d,?23}" HAYSTACK) ["abcd" "abcd-23"]))
    (is (= (glob/glob "[^cb]{?,*[-Q]{xxx,[0-9]?}}" HAYSTACK) ["abcd-23" "ff"]))
    (is (= (count (glob/glob "a{aaa,bcd{,-23}}" HAYSTACK)) 3))))

(deftest explode
  (is (= (glob/explode "[ab][cd]") ["ac" "ad" "bc" "bd"]))
  (is (=
       (glob/explode "{202{2-1[12],3-0[1-3]}}")
       ["2022-11" "2022-12" "2023-01" "2023-02" "2023-03"])))

(deftest errors
  (is (thrown? ExceptionInfo (glob/parse "{aaa")))
  (is (vector? (glob/parse "aaa}")))
  (is (thrown? ExceptionInfo (glob/explode "a*b"))))

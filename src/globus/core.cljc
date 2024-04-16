(ns globus.core
  "Bash-like globbing patterns for Clojure(Script).

  Supported pattern language constructs:

  * `?`: matches any single character
  * `*`: matches any string, including the empty string
  * `[qwe]`: matches any single character in `#{\\q \\w \\e}`
  * `[^qwe]` or `[!qwe]`: matches any single character _not_ in `#{\\q \\w \\e}`
  * `[a-f]`: matches any single character between `\\a` and `\\f` (inclusive)
  * `{one,two,three}`: matches anything that any of the patterns `one`, `two`
  and `three` would match."
  (:require
    [clojure.string :as str]))

(defn tokenize [s]
  (->> (conj (seq s) nil)
    (partition 2 1)
    (keep (fn [[p c]]
            (if (= p \\) [c true]
              (when (not= c \\) [c false]))))))

(defn- parse* [in {:keys [finalize] :or {finalize identity} :as specials}]
  (loop [rv []
         [[c bs] & in] in]
    (cond
      (not c) [(finalize rv) nil in]
      bs (recur (conj rv c) in)

      (contains? specials c)
      (if-let [sp (get specials c)]
        (let [[v in'] (sp c in)]
          (recur (conj rv v) in'))
        [(finalize rv) c in])

      :else (recur (conj rv c) in))))

(defn- finalize-cset [tag rv]
  (into [tag]
    (for [x (re-seq #".-.|." (apply str rv)) :let [c (first x)]]
      (if (= 1 (count x)) c [:rng c (last x)]))))

(defn parse-cset [p in]
  (let [[[c bs] & in'] in
        [tag in] (if (and c (not bs) (#{\^ \!} c)) [:rhc in'] [:chr in])
        [cs c in'] (parse* in {\] nil :finalize #(finalize-cset tag %)})]
    (if-not c
      (throw (ex-info "unclosed cset" {:in in}))
      [cs c in'])))

(declare parse-seq)

(defn parse-alt [p in]
  (loop [rv [:alt]
         in in]
    (let [[sq c in'] (parse-seq in {\} nil \, nil})]
      (if-not c
        (throw (ex-info "unclosed alt" {:in in}))
        (let [rv (conj rv sq)]
          (if (= c \,)
            (recur rv in')
            [rv c in']))))))

(defn- finalize-seq [rv]
  (->> rv
    (partition-by char?)
    (mapcat #(if (char? (first %)) [(into [:str] %)] %))
    (into [:seq])))

(defn parse-seq [in specials]
  (parse* in
    (merge specials
      {\* #(vector [:*] %2)
       \? #(vector [:?] %2)
       \[ #(let [[rv c in] (parse-cset %1 %2)]
             [rv in])
       \{ #(let [[rv c in] (parse-alt %1 %2)]
             [rv in])
       :finalize finalize-seq})))

(defn parse [s]
  (-> s tokenize (parse-seq nil) first))

(defn- char-code [c]
  #?(:clj (int c) :cljs (.charCodeAt c 0)))

(defn explode-ast [[tag & data :as ast]]
  (case tag
    :str [(str/join data)]
    :alt (mapcat explode-ast data)
    :seq (reduce
           #(vec (for [x %1 y (explode-ast %2)] (str x y)))
           [""]
           data)
    :chr (reduce
           #(if (char? %2)
              (conj %1 (str %2))
              (into %1 (map (comp str char)
                         (range (char-code (%2 1)) (inc (char-code (%2 2)))))))
           []
           data)
    (throw (ex-info "glob cannot be exploded" {:tag tag :data data}))))

(defn explode
  "enumerate all possible expansions of a pattern.
  (throws an exception if that is impossible for `pat`)"
  [pat]
  (-> pat parse explode-ast))

(def ^:private RE-SPECIALS
  (into {} (for [c "()[]{}\\+*.?|^$"] [c (str \\ c)])))

(defn- cset->s [cs]
  (if (char? cs)
    (get RE-SPECIALS cs cs)
    (str/join (replace RE-SPECIALS [(cs 1) "-" (cs 2)]))))

(defn ast->regex [[tag & data :as ast]]
  (case tag
    :str (->> data (replace RE-SPECIALS) str/join)
    :alt (str "(?:(?:" (str/join ")|(?:" (map ast->regex data)) "))")
    :seq (str/join (map ast->regex data))
    :chr (str "[" (str/join (map cset->s data)) "]")
    :rhc (str "[^" (str/join (map cset->s data)) "]")
    :* ".*"
    :? "."))

(defn- wrap-cljs-regex [s]
  (str \^ s \$))

(defn pattern->regex
  "Convert glob pattern `pat` into a regular expression"
  ([pat] (pattern->regex pat nil))
  ([pat {:keys [ignorecase]}]
   (cond->> (-> pat parse ast->regex #?(:cljs wrap-cljs-regex))
     ignorecase (str "(?i)"))))

(defn glob
  "Filter sequence of strings `sq` using pattern `pat`"
  ([pat sq] (glob pat sq nil))
  ([pat sq {:keys [ignorecase] :as options}]
   (let [re (re-pattern (pattern->regex pat options))]
     (filter #(re-matches re %) sq))))

(defn ast->string [[tag & data :as ast]]
  (case tag
    :seq (reduce
           #(if-let [s (ast->string %2)]
              (str %1 s)
              (reduced nil))
           ""
           data)
    :str (apply str data)
    nil))

(defn glob?
  "Check if a string looks like a glob pattern"
  [s]
  (not= s (ast->string (parse s))))

(comment
  (parse "ab[^ab0-9-q]c{a*,bb{c,*d}a}")
  (explode "ab[a-f]{g,h,}")
  (->> "a(b{g*,h,}[![d-f]" parse ast->regex)

  (explode "2022-{0[89],1{0,1}}")
  (glob? "10.12.2021")

  (glob "{a*,bc[de]}?" ["aaa" "bceq" "bbbb" "cqqq"])
  (glob "{A*,bc[de]}?" ["aaa" "bceq" "bbbb" "cqqq"])
  (glob "{A*,bc[de]}?" ["aaa" "bceq" "bbbb" "cqqq"] {:ignorecase true})
  ;;
  )

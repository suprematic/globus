(ns glob.core
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

(defn explode-ast [ast]
  (case (first ast)
    :str [(str/join (rest ast))]
    :alt (mapcat explode-ast (rest ast))
    :seq (reduce
           #(for [x %1 y (explode-ast %2)] (str x y))
           [""]
           (rest ast))
    :chr (reduce
           #(if (char? %2)
              (conj %1 (str %2))
              (into %1 (map (comp str char)
                         (range (int (%2 1)) (inc (int (%2 2)))))))
           []
           (rest ast))
    (throw (ex-info "glob cannot be exploded" {:ast ast}))))

(defn explode [pat]
  (-> pat parse explode-ast))

(def ^:private RE-SPECIALS
  (into {} (for [c "()[]{}\\+*.?|^$"] [c (str \\ c)])))

(defn- cset->s [cs]
  (if (char? cs)
    (get RE-SPECIALS cs cs)
    (str/join (replace RE-SPECIALS [(cs 1) "-" (cs 2)]))))

(defn ast->regex [ast]
  (case (first ast)
    :str (->> ast rest (replace RE-SPECIALS) str/join)
    :alt (str "(?:(?:" (str/join ")|(?:" (map ast->regex (rest ast))) "))")
    :seq (str/join (map ast->regex (rest ast)))
    :chr (str "[" (str/join (map cset->s (rest ast))) "]")
    :rhc (str "[^" (str/join (map cset->s (rest ast))) "]")
    :* ".*"
    :? "."))

(defn pattern->regex [pat]
  (-> pat parse ast->regex))

(defn glob [pat sq]
  (let [re (re-pattern (pattern->regex pat))]
    (filter #(re-matches re %) sq)))

(defn ast->string [ast]
  (case (first ast)
    :seq (reduce
           #(if-let [s (ast->string %2)]
              (str %1 s)
              (reduced nil))
           ""
           (rest ast))
    :str (apply str (rest ast))
    nil))

(defn glob? [s]
  (= s (ast->string (parse s))))

(comment
  (parse "ab[^ab0-9-q]c{a*,bb{c,*d}a}")
  (explode "ab[a-f]{g,h,}")
  (->> "a(b{g*,h,}[![d-f]" parse ast->regex)

  (explode "2022-{0[89],1{0,1}}")
  (glob? "10.12.2021")

  (glob "{a*,bc[de]}?" ["aaa" "bceq" "bbbb" "cqqq"])
  ;;
  )

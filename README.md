# Globus

Bash-like globbing patterns for Clojure(Script).

Supported pattern language constructs:

* `?`: matches any single character
* `*`: matches any string, including the empty string
* `[qwe]`: matches any single character in `#{\q \w \e}`
* `[^qwe]` or `[!qwe]`: matches any single character _not_ in `#{\q \w \e}`
* `[a-f]`: matches any single character between `\a` and `\f` (inclusive)
* `{one,two,three}`: matches anything that any of the patterns `one`, `two`
  and `three` would match. This one is has slightly different semantics
  than what e.g. bash does (because in bash curly braces are not actually a
  part of the matching engine)

See the [tests](test/globus/) for more details and examples.

## Usage

```clojure
(require '[globus.core :as glob])

;; check if a string is a glob pattern (contains special symbols)
(glob/glob? "abc") ; => false
(glob/glob? "ab?c") ; => true

;; filter a sequence of strings
(glob/glob "a*" ["aaa" "bb" "cccc" "defg"]) ; => ("aaa")
(glob/glob "[^a]???" ["aaa" "bb" "cccc" "defg"]) ; => ("cccc" "defg")

;; convert a pattern to a (string representation of a) regular expression
(glob/pattern->regex "{a.*,*b}") ; => "(?:(?:a\\..*)|(?:.*b))"

;; enumerate all possible expansions of a pattern
(glob/explode "[ab]{cd,efg,hi[j-m]}")
; =>
; ["acd"
;  "aefg"
;  "ahij"
;  "ahik"
;  "ahil"
;  "ahim"
;  "bcd"
;  "befg"
;  "bhij"
;  "bhik"
;  "bhil"
;  "bhim"]

;; that fails for patterns that cannot be expanded (contain `*`, `?` or
;; `[^...])
(glob/explode "abc*") ; Exception is thrown
```

## Contributing

Run the project's tests:

    $ clojure -T:build test
    $ clojure -T:build test-node # ClojureScript tests

## License

Copyright © 2022 Suprematic

Distributed under the Eclipse Public License.

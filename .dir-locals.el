((clojure-mode
  ;; (cider-clojure-cli-global-options . "-M:provided:test")
  (cider-clojure-cli-parameters . "-M:provided:test -m nrepl.cmdline --middleware '%s'")
  (clojure-local-source-path . "src/core")
  (clojure-local-source-test-path . "src/test")))

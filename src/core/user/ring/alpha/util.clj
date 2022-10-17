(ns user.ring.alpha.util
  (:require
   [clojure.java.io :as jio]
   [clojure.string :as str]
   ))


(defn strip-left-slash
  "Return just path(slashed) if path is absolute, otherwise return path(slashed) with parent."
  [s]
  (if (str/starts-with? s "/")
    (subs s 1)
    s))


(defn strip-right-slash
  "Return just path(slashed) if path is absolute, otherwise return path(slashed) with parent."
  [s]
  (if (str/ends-with? s "/")
    (subs s 0 (dec (count s)))
    s))


(defn slash-absolutize
  "Return absoulute path(slashed)."
  [s]
  (if (str/starts-with? s "/")
    s
    (str "/" s)))


(defn file
  [root-dir path]
  (let [root (jio/as-file root-dir)]
    (when (and root (.isDirectory root))
      (let [file (jio/file root (strip-left-slash path))]
        (when (.isFile file)
          file)))))


(defn file-or-resource
  [root-dir path]
  (or (file root-dir path)
      (when (string? path)
        (jio/resource (strip-left-slash path)))))

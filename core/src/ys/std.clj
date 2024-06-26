;; Copyright 2023-2024 Ingy dot Net
;; This code is licensed under MIT license (See License for details)

;; This is the YAMLScript standard library.

(ns ys.std
  (:require
   [yamlscript.debug]
   [babashka.fs :as fs]
   [babashka.http-client :as http]
   [babashka.process :as process]
   [clojure.pprint :as pp]
   [clojure.string :as str]
   [flatland.ordered.map]
   [ys.ys :as ys]
   [yamlscript.common :as common]
   [yamlscript.util :as util])
  (:refer-clojure :exclude [num
                            print
                            when]))

(declare die)
(intern 'ys.std 'die util/die)

;; TODO fix this with a talk to remove _T calls from output
(defmacro _T [xs]
  (let [[fun# & args#] xs
        args# (map pr-str args#)
        #_#_args# (map (fn [x]
                     (let [y (str/replace x #"\(_T " "")
                           n (/ (- (count x) (count y)) 4)]
                       (subs y 0 (- (count y) n)))) args#)
        args# (str/join " -> " args#)]
    `(do
       (clojure.core/print
         ";;" '~fun# "->" ~args# "\n")
       (~@xs))))

;; TODO replace with p()
(defn www [& xs]
  (apply yamlscript.debug/www xs))

(defn xxx [& xs]
  (apply yamlscript.debug/xxx xs))

(defn yyy [& xs]
  (apply yamlscript.debug/yyy xs))

(defn zzz [& xs]
  (apply yamlscript.debug/zzz xs))

(defn toBool [x] (boolean x))

(defn toFloat [x] (parse-double x))

(defn toInt [x] (parse-long x))

(defn toMap
  ([] {})
  ([x] (apply hash-map x))
  ([k v & xs] (apply hash-map k v xs)))

(defn toStr [& xs] (apply str xs))

; toList
; toNum
; toVec

(defn _& [sym val]
  (swap! common/stream-anchors_ assoc sym val)
  (swap! common/doc-anchors_ assoc sym val)
  val)

(defn _* [sym]
  (or
    (get @common/doc-anchors_ sym)
    (die "Anchor not found: &" sym)))

(defn _** [sym]
  (or
    (get @common/stream-anchors_ sym)
    (die "Anchor not found: &" sym)))

(defn $$ [] (->> @common/$# str keyword (get @common/$)))

(defn +++* [value]
  (let [index (keyword (str (swap! common/$# inc)))]
    (reset! common/doc-anchors_ {})
    (swap! common/$ assoc index value)
    value))

(defmacro +++ [& forms]
  `(~'+++* (do ~@forms)))

(defn _dot [ctx key]
  (cond
    (symbol? key) (or
                    (get ctx (keyword key))
                    (get ctx (str key))
                    (get ctx key))
    (string? key) (get ctx key)
    (int? key) (let [n (if (< key 0)
                         (+ key (count ctx))
                         key)]
                 (if (map? ctx)
                   (or (get ctx (keyword (str n)))
                     (get ctx (str n))
                     (get ctx n))
                   (nth ctx n)))
    (keyword? key) (get ctx key)
    (list? key) (let [[fnc & args] key
                      nargs (map #(if (= '_ %1) ctx %1) args)
                      args (vec
                             (if (or (nil? args) (= nargs args))
                               (cons ctx args)
                               nargs))
                      value (apply fnc args)
                      value (if (instance? clojure.lang.LazySeq value)
                              (vec value)
                              value)]
                  value)
    :else (die "Invalid key: " key)))

(defn __ [x & xs]
  (reduce _dot x xs))

(declare num)
(defn +_ [x & xs]
  (cond
    (string? x) (apply str x xs)
    (vector? x) (apply concat x xs)
    (map? x) (apply merge x xs)
    :else (apply + x (map num xs))))

(defn *_
  ([x y]
   (cond
     (and (string? x) (number? y)) (apply str (repeat y x))
     (and (number? x) (string? y)) (apply str (repeat x y))
     (and (sequential? x) (number? y)) (apply concat (repeat y x))
     (and (number? x) (sequential? y)) (apply concat (repeat x y))
     :else  (* x y)))
  ([x y & xs]
    (reduce *_ (*_ x y) xs)))

(defn =-- [str rgx]
  (re-find rgx str))

(defn abspath [& args]
  (apply util/abspath args))

(defn cwd [& args]
  (str (apply babashka.fs/cwd args)))

(defn curl [url]
  (let [url (if (re-find #":" url)
              url
              (str "https://" url))
        resp (http/get url)]
    (if-let [body (:body resp)]
      (str body)
      (die resp))))

(defn dirname [& args]
  (apply util/dirname args))

(defmacro each [bindings & body]
  `(do
     (doall (for [~@bindings] (do ~@body)))
     nil))

(defn err [& xs]
  (binding [*out* *err*]
    (apply clojure.core/print xs)
    (flush)))

(defn exec [cmd & xs]
  (apply process/exec cmd xs))

(defn fs-d [p] (fs/directory? p))
(defn fs-e [p] (fs/exists? p))
(defn fs-f [p] (fs/regular-file? p))
(defn fs-l [p] (fs/sym-link? p))
(defn fs-r [p] (fs/readable? p))
(defn fs-s [p] (not= 0 (fs/size p)))
(defn fs-w [p] (fs/writable? p))
(defn fs-x [p] (fs/executable? p))
(defn fs-z [p] (= 0 (fs/size p)))

(defn fs-cwd [] (str (fs/cwd)))
(defn fs-ls
  ([] (fs-ls "."))
  ([d] (map str (fs/list-dir d))))
(defn fs-mtime [f]
  (fs/file-time->millis
    (fs/last-modified-time f)))
(defn fs-glob
  ([pat] (fs-glob "." pat))
  ([dir pat] (map str (fs/glob dir pat))))
(defn fs-path-abs [p] (str (fs/absolutize p)))
(defn fs-path-rel
  ([p] (str (fs/relativize (fs/cwd) p)))
  ([d p] (str (fs/relativize d p))))
(defn fs-which [c]
  (when-let [p (fs/which c)] (str p)))

(defn grep [a b]
  (let [[a b] (if (seqable? b) [a b] [b a])
        _ (when-not (seqable? b) (die "No seqable arg passed to grep"))
        t (type a)]
    (cond
      (= t java.util.regex.Pattern) (filter #(re-find a %1) b)
      (fn? a) (filter a b)
      :else (die "Invalid args for grep"))))

(defn join
  ([xs] (join "" xs))
  ([sep & xs]
    (if (= 1 (count xs))
      (str/join sep (first xs))
      (str/join sep xs))))

(defn new [class & args]
  (clojure.lang.Reflector/invokeConstructor
    class (into-array Object args)))

(defn num [x]
  (condp = (type x)
    java.lang.Boolean (if x 1 0)
    java.lang.Long (long x)
    java.lang.Double (double x)
    java.lang.String (or
                       (if (re-find #"\." x)
                         (parse-double x)
                         (parse-long x))
                       0)
    clojure.lang.PersistentVector (count x)
    clojure.lang.PersistentList (count x)
    clojure.lang.PersistentArrayMap (count x)
    clojure.lang.PersistentHashMap (count x)
    clojure.lang.PersistentHashSet (count x)
    (die (str "Can't convert " (type x) " to number"))))

(defn omap [& xs]
  (apply flatland.ordered.map/ordered-map xs))

(defn out [& xs]
  (apply clojure.core/print xs)
  (flush))

(defn pow [x y]
  (Math/pow x y))

(defn pp [o]
  (pp/pprint o))

(defn pretty [o]
  (str/trim-newline
    (with-out-str
      (pp/pprint o))))

(defn print [& xs]
  (apply clojure.core/print xs)
  (flush))

(defn process [cmd & xs]
  (apply process/process cmd xs))

(defmacro q [x]
  `(quote ~x))

(defn rng [x y]
  (if (> y x)
    (range x (inc y))
    (range x (dec y) -1)))

(defn say [& xs]
  (apply clojure.core/println xs))

(defn sh [cmd & xs]
  (apply process/sh cmd xs))

(defn shell [cmd & xs]
  (apply process/shell cmd xs))

(defn sleep [s]
  (Thread/sleep (int (* 1000 s))))

(defn throw [e] (throw e))

(defn use-pod [pod-name version]
  (ys/load-pod pod-name version))

(defn warn [& xs]
  (binding [*out* *err*]
    (apply clojure.core/println xs)
    (flush)))

(comment
  )

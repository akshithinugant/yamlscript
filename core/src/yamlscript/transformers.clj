;; Copyright 2023-2024 Ingy dot Net
;; This code is licensed under MIT license (See License for details)

(ns yamlscript.transformers
  (:require
   [yamlscript.util :refer [if-lets when-lets]]
   [yamlscript.ast :refer [Sym Lst Vec Key]]
   [yamlscript.debug :refer [www]]))

(def Q {:Sym 'quote})

(defn transform_require [node]
  (let [[key val] (:pairs node)]
    (if-lets [_ (:Sym key)
              _ (:Spc val)]
      {:pairs [key (Lst [Q val])]}
      ,
      (if-lets [sym (get-in key [0])
                _ (:Sym sym)
                spc (nth key 1)
                _ (:Spc spc)
                _ (= 2 (count key))
                _ (nil? val)]
        {:pairs [sym (Lst [Q spc])]}
        ,
        (if-lets [sym (get-in key [0])
                  _ (:Sym sym)
                  spc (nth key 1)
                  _ (:Spc spc)
                  _ (= 2 (count key))
                  _ (= '=> (get-in val [0 :Sym]))
                  alias (nth val 1)
                  _ (:Sym alias)]
          {:pairs [sym (Lst [Q (Vec [spc (Key "as") alias])])]}
          node)))))

(comment
  www)
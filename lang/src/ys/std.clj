;; Copyright 2023 Ingy dot Net
;; This code is licensed under MIT license (See License for details)

;; This is the YAMLScript standard library.

(ns ys.std
  (:require
   [yamlscript.debug :refer [www]]))

(defn say [& more]
  (apply clojure.core/println more))

(comment
  )
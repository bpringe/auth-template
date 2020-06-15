(ns simplefi.util)

(defn generate-uuid
  []
  (.toString (java.util.UUID/randomUUID)))
(ns auth-template.util)

(defn generate-uuid
  []
  (.toString (java.util.UUID/randomUUID)))
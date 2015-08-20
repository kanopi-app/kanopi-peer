(ns kanopi.util.core
  (:import java.util.UUID))

(defn select-with-merge
  "Selects subset of a larger configuration map, merging in the selected keys.
   Selection can be a vector, merge-keys must be a vector. In a conflict, the
   merge-keys take precedence."
  [config selection merge-keys]
  (let [get-or-in (fn [m k]
                    (if (coll? k)
                      (get-in m k)
                      (get m k)))
        subset    (get-or-in config selection)]
    (reduce (fn [m k]
              (assoc m k (get-or-in config k)))
            subset
            merge-keys)))

(defn random-uuid []
  (java.util.UUID/randomUUID))

;; ### HTTP context map helper fns

(defn get-authenticator [ctx]
  (get-in ctx [:request :authenticator]))

(defn get-data-service [ctx]
  (get-in ctx [:request :data-service]))

;; ### Datomic EntityMap helper fns for navigating the schema
;; TODO: refactor to support values of any type
(defn fact-entity->tuple [ent]
  (let [attr (-> ent :fact/attribute :thunk/label)
        valu (-> ent :fact/value     :value/string)]
    (vector attr valu)))

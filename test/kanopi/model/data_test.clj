(ns kanopi.model.data-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.set]
            [schema.core :as s]
            [datomic.api :as d]
            [kanopi.util.core :as util]
            [kanopi.model.schema :as schema]
            [kanopi.model.data :as data]
            [kanopi.model.data.impl :as impl]
            [kanopi.model.storage.datomic :as datomic]
            [kanopi.view.auth :as auth]
            [kanopi.test-util :as test-util]
            [com.stuartsierra.component :as component]))

(deftest init-datum
  (let [system (component/start (test-util/system-excl-web))

        creds  (do (auth/register! (:authenticator system) "brian" "rubinton")
                   (auth/credentials (:authenticator system) "brian"))
        ent-id (data/init-datum (:data-service system) creds)
        ent    (data/get-datum (:data-service system) creds ent-id)]

    (testing "init datum returns the datum's entity id"
      (is (s/validate schema/DatomicId ent-id)))
    
    (testing "datum has user's default team"
      (is (= (impl/user-default-team creds) (-> ent :datum/team :db/id))))

    (testing "datum shape as given by data service"
      (is (s/validate schema/Datum ent)))

    (testing "retract new datum"
      (let [report (data/retract-datum (:data-service system) creds ent-id)]
        (is (nil? (data/get-datum (:data-service system) creds ent-id)))))

    (component/stop system)))

(deftest construct-datum
  (let [system (component/start (test-util/system-excl-web))
        creds  (do (auth/register! (:authenticator system) "brian" "rubinton")
                   (auth/credentials (:authenticator system) "brian"))

        ent-id (data/init-datum (:data-service system) creds)
        ent-0  (data/get-datum (:data-service system) creds ent-id)
        fact-1 ["age" "42"]
        ent-1  (apply data/add-fact (:data-service system) creds ent-id fact-1)
        fact-id (-> ent-1 :datum/fact first :db/id)
        ]

    (testing "assert fact"
      (let [new-fact (->> (clojure.set/difference (set (get ent-1 :datum/fact))
                                                  (set (get ent-0 :datum/fact)))
                          (first))]
        (is (= (util/fact-entity->tuple new-fact) fact-1))))

    (testing "update-fact: change value literal. attr label same."
      (let [fact' ["age" "new-value!"]
            fact-ent (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= fact' (util/fact-entity->tuple fact-ent)))))

    (testing "update-fact: change value literal. attr label nil."
      (let [fact' [nil "new-value2!"]
            fact-ent (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= fact' (util/fact-entity->tuple fact-ent)))))

    (testing "update-fact: change value from literal to ref"
      (let [fact' ["age2" [:db/id "42"]]
            fact-ent (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= ["age2" "42"] (util/fact-entity->tuple fact-ent)))))

    (testing "update-fact: change value from ref to literal"
      (let [fact' ["age2" "43"]
            fact-ent  (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= fact' (util/fact-entity->tuple fact-ent)))))

    (testing "update-fact: change attribute from literal to ref"
      (let [fact' ["age2" [:db/id "infinity"]]
            fact-ent (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= ["age2" "infinity"] (util/fact-entity->tuple fact-ent)))))

    (testing "update-fact: change attribute from ref to literal"
      (let [fact' ["age2" "a real number"]
            fact-ent (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= fact' (util/fact-entity->tuple fact-ent)))))

    (testing "update datum label"
      (let [lbl' "new label dude!"
            datum' (data/update-datum-label (:data-service system) creds ent-id lbl')]
        (is (= lbl' (get datum' :datum/label)))))
    
    (is (s/validate schema/Datum (data/get-datum (:data-service system) creds ent-id))
        "No longer valid!")))

(deftest literal-types
  (let [system  (component/start (test-util/system-excl-web))
        creds   (do (auth/register!   (:authenticator system) "brian" "rubinton")
                    (auth/credentials (:authenticator system) "brian"))
        ent-id  (data/init-datum (:data-service system) creds)
        ent-0   (data/get-datum  (:data-service system) creds ent-id)
        fact-1  ["age" "42"]
        ent-1   (apply data/add-fact (:data-service system) creds ent-id fact-1)
        fact-id (-> ent-1 :datum/fact first :db/id)
        ]
    (testing "tagged text literal"
      (let [fact' ["age" [:literal/text "new-value!"]]
            fact-ent (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= ["age" "new-value!"] (util/fact-entity->tuple fact-ent)))))

    (testing "tagged integer literal"
      (let [fact' ["age" [:literal/integer 42]]
            fact-ent (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= ["age" 42] (util/fact-entity->tuple fact-ent)))))

    (testing "tagged decimal literal"
      (let [fact' ["age" [:literal/decimal 73.65]]
            fact-ent (apply data/update-fact (:data-service system) creds fact-id fact')]
        (is (= ["age" 73.65] (util/fact-entity->tuple fact-ent)))))

    (testing "tagged uri literal")
    (testing "tagged email-address literal")

    (component/stop system)
    ))

(deftest search-datums
  (let [system  (component/start (test-util/system-excl-web))
        data-svc (get system :data-service)
        creds   (do (auth/register!   (:authenticator system) "brian" "rubinton")
                    (auth/credentials (:authenticator system) "brian"))
        ]
    (testing "nothing"
      (let [results (data/search-datums data-svc creds "foojahBOOHJAH")]
        (is (empty? results))))

    (testing "pale"
      (let [results (data/search-datums data-svc creds "pale")]
        (is (not-empty results))))

    (component/stop system)))

(defn- datums-with-titles [db]
  (d/q '[:find [?datum ...]
         :where
         [?datum :datum/fact ?fact]
         [?fact :fact/attribute ?attr]
         [?attr :datum/label "Title"]
         ]
       db))

(defn- get-db [sys]
  (d/db (get-in sys [:datomic-peer :connection])))

(deftest context-datums
  (let [{data-svc :data-service :as system}
        (component/start (test-util/system-excl-web))

        creds  (do (auth/register!   (:authenticator system) "brian" "rubinton")
                   (auth/credentials (:authenticator system) "brian"))
        db     (get-db system)
        datum-id (d/q '[:find ?s . :where [?s :datum/label "David Foster Wallace"]] db)
        results (data/context-datums data-svc creds datum-id)
        ]
    (is (not-empty results))

    (testing "context-datums are all datums"
      (is (->> results
               (map first)
               (map (comp :datum/label (partial d/entity db)))
               (every? identity))))

    (testing "one step removed")

    (component/stop system)))

(deftest similar-datums
  (let [
        {data-svc :data-service :as system}
        (component/start (test-util/system-excl-web))
        creds  (do (auth/register!   (:authenticator system) "brian" "rubinton")
                   (auth/credentials (:authenticator system) "brian"))
        db     (get-db system)
        datum-ids (datums-with-titles db)
        results (data/similar-datums data-svc creds (first datum-ids))
        ]
    (is (not-empty results))
    (testing "all books with titles are similar to each other"
      (let []
        (clojure.set/subset? (set datum-ids)
                             (set (cons (first datum-ids) (map first results))))
        ))

    (testing "similar datums are all datums"
      (is (->> results
               (map first)
               (map (comp :datum/label (partial d/entity db)))
               (every? identity)
               )))

    (component/stop system)
    ))

(deftest most-edited-datums
  (let [
        system (component/start (test-util/system-excl-web))
        creds  (do (auth/register!   (:authenticator system) "brian" "rubinton")
                   (auth/credentials (:authenticator system) "brian"))
        ]
    ;(is false)
    (component/stop system)
    ))

(deftest most-viewed-datums
  (let []
    ;(is false)
    ))

(deftest recent-datums
    (let [{data-svc :data-service :as system}
          (component/start (test-util/system-excl-web))

          creds (auth/credentials (:authenticator system) "hannah")
          results (data/recent-datums data-svc creds)
          db (get-db system)
          ]
      ;(is (not-empty results))
      (println "HERE" results)
      (component/stop system)))

;;(deftest authorization-controls
;;  (let [creds-a nil
;;        creds-b nil
;;        creds-c nil]
;;    ))

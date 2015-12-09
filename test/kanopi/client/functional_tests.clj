(ns kanopi.client.functional-tests
  "Verify integrity of app state against an onslaught of input
  messages conceivably generated by a user. Further, verify request
  and response handlers tying everything together."
  (:require [com.stuartsierra.component :as component]
            [clojure.test :refer :all]
            [clojure.pprint :refer (pprint)]
            
            [schema.core :as s]
            
            [kanopi.system.server :as server]
            [kanopi.system.client :as client]
            
            [kanopi.test-util :as test-util]
            [kanopi.util :as util]
            ))

(deftest access-anon-session
  (let [client-system (component/start (test-util/initialized-client-system))
        ]

    (component/stop client-system)))

(deftest create-data
  (let []
    ))

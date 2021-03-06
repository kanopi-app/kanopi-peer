(ns kanopi.view.web-app
  (:require [com.stuartsierra.component :as component]
            [immutant.web.middleware :as immutant-session]
            [liberator.dev :refer [wrap-trace]]
            [liberator.representation :as rep]
            [cemerick.friend :as friend]
            [cemerick.friend.workflows :as workflows]
            [cemerick.friend.credentials :as creds]
            [ring.middleware.defaults]
            [ring.middleware.params]
            [ring.middleware.keyword-params]
            [ring.middleware.cookies]
            [ring.middleware.format]
            [kanopi.util.core :as util]))

(defn authentication-middleware
  [handler credential-fn]
  (let [friend-m
        {
         :allow-anon?       true
         :redirect-on-auth? false
         :credential-fn     (partial creds/bcrypt-credential-fn credential-fn)

         :default-landing-uri "/"
         :login-uri "/login"

         ;; TODO: make better error handlers which return errors
         ;; described as data
         :login-failure-handler   (constantly {:status 401})
         :unauthenticated-handler (constantly {:status 401})
         :unauthorized-handler    (constantly {:status 401})

         :workflows [
                     (workflows/interactive-form :redirect-on-auth? false :allow-anon? true)
                     (workflows/http-basic :realm "/")
                     ]}]
    (-> handler
        (friend/authenticate friend-m))))

(defn wrap-ensure-session
  "Ensures that a session exists and has a key set. Also ensures that it's added
  to the response if missing."
  [h]
  (fn [req]
    (let [session (:session req)
          has-key? (contains? session :key)
          key (or (:key session) (str (util/random-uuid)))
          response (h (cond-> req
                        (not has-key?)
                        (assoc-in [:session :key] key)))]
      (cond

       (or has-key?
           (= key (get-in response [:session :key]))
           (and (contains? response :session) (nil? (:session response))))
       response

       (:session response)
       (assoc-in response [:session :key] key)

       :else
       (assoc response :session (assoc session :key key))))))

(defn wrap-add-to-req [handler k payload]
  (fn [req]
    (handler (assoc req k payload))))

(defrecord WebApp [config data-service session-service app-handler authenticator]

  component/Lifecycle
  (start [this]
    (if app-handler
      this
      (let [http-handler ((:handler config))
            kanopi-session-name "kanopi-session"]
        (-> http-handler
            (authentication-middleware (:user-lookup-fn authenticator))
            (immutant-session/wrap-session
             {:timeout -1
              :cookie-name kanopi-session-name})
            (ring.middleware.defaults/wrap-defaults
             (-> ring.middleware.defaults/site-defaults
                 (dissoc :session)
                 (assoc :security false)))
            (ring.middleware.params/wrap-params)
            (ring.middleware.keyword-params/wrap-keyword-params)
            (ring.middleware.cookies/wrap-cookies)
            (cond-> (:dev config)
              (wrap-trace :header :ui))
            ;(wrap-ensure-session)
            (wrap-add-to-req :session-service session-service)
            (wrap-add-to-req :data-service data-service)
            (wrap-add-to-req :authenticator authenticator)
            (->> (assoc this :app-handler))))))

  (stop [this]
    (if-not app-handler this
      (assoc this :app-handler nil))))

(defn new-web-app [config]
  (map->WebApp {:config config}))

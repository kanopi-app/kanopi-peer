(ns kanopi.model.message
  (:require #?@(:cljs [[om.core :as om]
                       [kanopi.controller.history :as history]
                       [ajax.core :as ajax]
                       [cljs.core.async :as async] 
                       ]
                :clj  [[clojure.string]
                       [schema.core :as s]
                       [cemerick.friend :as friend]
                       [kanopi.model.schema :as schema]
                       [kanopi.util.core :as util]
                       ])))

;; Pure cross-compiled message generators
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-datum [datum-id]
  {:pre [(integer? datum-id)]}
  (hash-map
   :noun datum-id
   :verb :get-datum
   :context {}))

(defn update-fact [datum-id fact]
  (hash-map
   :noun {:datum-id datum-id
          :fact fact}
   :verb :update-fact
   :context {}))

(defn update-datum-label [ent new-label]
  (hash-map
   :noun {:existing-entity ent
          :new-label new-label}
   :verb :update-datum-label
   :context {}))

(defn initialize-client-state [user]
  (hash-map
   :noun user
   :verb :initialize-client-state
   :context {}))


(defn search
  ([q]
   (search q nil))
  ([q tp]
   (hash-map
    :noun {:query-string q
           :entity-type  tp}
    :verb :search
    :context {})))


;; Server-only utilities for parsing messages out of ring request maps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#?(:clj
   (defn- request->noun [ctx noun]
     {:post [(or (integer? %) (instance? java.lang.Long %) (map? %))]}
     noun))

#?(:clj
   (defn- request->verb [ctx verb]
     {:post [(keyword? %)]}
     verb))

#?(:clj
   (defn- request->context [request-context message-context]
     {:post [(map? %)]}
     (let [creds (-> (friend/current-authentication (:request request-context))
                     :identity
                     ((util/get-auth-fn request-context))
                     )]
       (s/validate schema/Credentials creds)
       (assoc message-context :creds creds))))

#?(:clj
   (defn remote->local
     "If for some reason the request is in some way logically incomplete,
     here's the place to indicate that."
     ([ctx]
      (let [body        (util/transit-read (get-in ctx [:request :body]))
            params      (get-in ctx [:request :params])
            parsed-body (->> (merge body params)
                             (reduce (fn [acc [k v]]
                                       (cond
                                        (string? v)
                                        (if (clojure.string/blank? v)
                                          (assoc acc k {})
                                          (assoc acc k (read-string v))) 

                                        :default
                                        (assoc acc k v)))
                                     {}))]
        (hash-map
         :noun    (request->noun    ctx (:noun    parsed-body))
         :verb    (request->verb    ctx (:verb    parsed-body))
         :context (request->context ctx (:context parsed-body)))))))

;; Client-only messages, aether helper fns, and local->remote
;; transformers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
#?(:cljs
   (defn publisher [owner]
     (om/get-shared owner [:aether :publisher]))
   )
#?(:cljs
   (defn send!
     "Ex: (->> (msg/search \"foo\") (msg/send! owner))
     TODO: allow specification of transducers or debounce msec via args
     - otherwise user must create some extra wiring on their end,
     which is what this fn is trying to avoid. layered abstractions.
     TODO: make work with different first args eg. a core.async channel,
     an aether map, an aether record, etc"
     ([owner msg & args]
      (async/put! (publisher owner) msg)
      ;; NOTE: js evt handlers don't like `false` as a return value, which
      ;; async/put! often returns. So we add a nil.
      nil))
   )
#?(:cljs
   (defn toggle-fact-mode [ent]
     (hash-map
      :noun [:fact (:db/id ent)]
      :verb :toggle-mode
      :context {}))
   )
#?(:cljs
   (defn select-fact-part-type [fact-id fact-part tp]
     (hash-map
      :noun [:fact fact-id]
      :verb :select-fact-part-type
      :context {:fact-part fact-part
                :value tp}))
   )
#?(:cljs
   (defn input-fact-part-value [fact-id fact-part input-value]
     (hash-map
      :noun [:fact fact-id]
      :verb :input-fact-part-value
      :context {:fact-part fact-part
                :value input-value}))
   )
#?(:cljs
   (defn select-fact-part-reference [fact-id fact-part selection]
     (hash-map
      :noun [:fact fact-id]
      :verb :select-fact-part-reference
      :context {:fact-part fact-part
                :selection selection}))
   )
#?(:cljs
   (defn register [creds]
     (hash-map 
      :noun creds
      :verb :register
      :context {})))
#?(:cljs
   (defn register-success [creds]
     (hash-map
      :noun creds
      :verb :register-success
      :context {}))
   )
#?(:cljs
   (defn register-failure [err]
     (hash-map
      :noun err
      :verb :register-failure
      :context {}))
   )
#?(:cljs
   (defn login [creds]
     (hash-map
      :noun creds
      :verb :login
      :context {}))
   )
#?(:cljs
   (defn login-success [creds]
     (hash-map
      :noun creds
      :verb :login-success
      :context {}))
   )
#?(:cljs
   (defn login-failure [err]
     (hash-map
      :noun err
      :verb :login-failure
      :context {}))
   )
#?(:cljs
   (defn logout []
     (hash-map
      :noun nil
      :verb :logout
      :context {}))
   )
#?(:cljs
   (defn logout-success [foo]
     (hash-map
      :noun foo
      :verb :logout-success
      :context {}))
   )
#?(:cljs
   (defn logout-failure [err]
     (hash-map
      :noun err
      :verb :logout-failure
      :context {}))
   )

#?(:cljs
   (defn valid-remote-message?
     "Some simple assertions on the shape of the remote message.
     It's not as willy-nilly as local messages, though that must change
     as well."
     [msg]
     (-> msg
         (get :noun)
         ((juxt :uri :method :response-method :error-method))
         (->> (every? identity))))
   )

#?(:cljs
   (do
    (defmulti local->remote
      (fn [history app-state msg]
        (println "local->remote" msg)
        (get msg :verb))
      :default :default)

    (defmethod local->remote :register
      [history app-state msg]
      {:post [(valid-remote-message? %)]}
      (hash-map
       :noun {:uri             (history/get-route-for history :register)
              :params          (get msg :noun)
              :method          :post
              :response-format :transit
              :response-method :aether
              :response-xform  register-success
              :error-method    :aether
              :error-xform     register-failure
              }
       :verb :request
       :context {}))

    (defmethod local->remote :login
      [history app-state msg]
      {:post [(valid-remote-message? %)]}
      (hash-map
       :noun {
              ;; NOTE: cljs-ajax parses params to req body for POST
              ;; requests. friend auth lib requires username and password
              ;; to appear in params or form-params, not body.
              :uri             (ajax/uri-with-params
                                (history/get-route-for history :login)
                                (get msg :noun))
              :method          :post
              :response-format :transit
              :response-method :aether
              :response-xform  login-success
              :error-method    :aether
              :error-xform     login-failure
              }
       :verb :request
       :context {}))

    (defmethod local->remote :logout
      [history app-state msg]
      {:post [(valid-remote-message? %)]}
      (hash-map
       :noun {:uri             (history/get-route-for history :logout)
              :method          :post
              :response-format :transit
              :response-method :aether
              :response-xform  logout-success
              :error-method    :aether
              :error-xform     logout-failure
              }
       :verb :request
       :context {}))

    (defmethod local->remote :default
      [history app-state msg]
      {:post [(valid-remote-message? %)]}
      (hash-map 
       :noun {:uri  (history/get-route-for history :api)
              :body msg
              :method :post
              :response-format :transit
              :response-method :aether
              :error-method    :aether
              }
       :verb :request
       :context {}))

    (defmethod local->remote :initialize-client-state
      [history app-state msg]
      {:post [(valid-remote-message? %)]}
      (hash-map
       :noun {:uri             (history/get-route-for history :api)
              :params          msg
              :format          :transit
              :method          :post
              :response-format :transit
              :response-method :aether
              :error-method    :aether}
       :verb :request
       :context {}))

    (defmethod local->remote :search
      [history app-state msg]
      {:post [(valid-remote-message? %)]}
      (hash-map
       :noun {:uri             (history/get-route-for history :api)
              :params          msg
              :format          :transit
              :method          :post
              :response-format :transit
              :response-method :aether
              :error-method    :aether}
       :verb :request
       :context {}))

(defmethod local->remote :get-datum
  [history app-state msg]
  {:post [(valid-remote-message? %)]}
  (hash-map
   :noun {:uri             (history/get-route-for history :api)
          :params          msg
          :format          :transit
          :method          :post
          :response-format :transit
          :response-method :aether
          :error-method    :aether
          }
   :verb :request
   :context {}))

(defmethod local->remote :update-datum-label
  [history app-state msg]
  {:post [(valid-remote-message? %)]}
  (hash-map
   :noun {:uri             (history/get-route-for history :api)
          :params          msg
          :format          :transit
          :method          :post
          :response-format :transit
          :response-method :aether
          :error-method    :aether
          }
   :verb :request
   :context {}))

(defmethod local->remote :update-fact
  [history app-state msg]
  {:post [(valid-remote-message? %)]}
  (hash-map
   :noun {:uri             (history/get-route-for history :api)
          :params          msg
          :method          :post
          :format          :transit
          :response-format :transit
          :response-method :aether
          :error-method    :aether}
   :verb :request
   :context {}))

))
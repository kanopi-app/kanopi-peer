(ns kanopi.controller.handlers.response
  (:require [om.core :as om]

            [taoensso.timbre :as timbre
             :refer-macros (log trace debug info warn error fatal report)]

            [kanopi.aether.core :as aether]
            [kanopi.controller.history :as history]
            [kanopi.model.message :as msg]
            
            [kanopi.util.core :as util]))

(defmulti local-response-handler
  (fn [_ _ _ msg]
    (info msg)
    (get msg :verb)))

(defn- record-error-message
  [app-state msg]
  (om/transact! app-state :error-messages #(conj % msg)))

(defn- incorporate-user-datum!
  [app-state user-datum]
  (om/transact! app-state
                (fn [app-state]
                  (let [datum-id (get-in user-datum [:datum :db/id])]
                    (-> app-state
                        (assoc :datum user-datum)
                        (assoc-in [:cache datum-id] (get user-datum :datum)))))))

(defn- incorporate-updated-datum!
  [app-state datum]
  (om/transact! app-state
                (fn [app-state]
                  (let [datum-id (get datum :db/id)]
                    (-> app-state
                        (assoc-in [:datum :datum] datum)
                        (assoc-in [:cache datum-id] datum))))))

(defmethod local-response-handler :datum.fact.add/success
  [aether history app-state msg]
  (incorporate-updated-datum! app-state (get msg :noun)))

(defmethod local-response-handler :datum.fact.add/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :datum.fact.update/success
  [aether history app-state msg]
  (incorporate-updated-datum! app-state (get msg :noun)))

(defmethod local-response-handler :datum.fact.update/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :datum.label.update/success
  [aether history app-state msg]
  (incorporate-updated-datum! app-state (get msg :noun)))

(defmethod local-response-handler :datum.label.update/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :datum.create/success
  [aether history app-state msg]
  (let [dtm (get-in msg [:noun :datum])]
    (incorporate-user-datum! app-state (get msg :noun))
    (history/navigate-to! history [:datum :id (get dtm :db/id)])))

(defmethod local-response-handler :datum.create/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :datum.get/success
  [aether history app-state msg]
  (incorporate-user-datum! app-state (get msg :noun)))

(defmethod local-response-handler :datum.get/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :spa.navigate.search/success
  [aether history app-state msg]
  (let [{:keys [query-string results]} (get msg :noun)]
    (om/transact! app-state :search-results
                  (fn [search-results]
                    (assoc search-results query-string results)))))

(defmethod local-response-handler :spa.navigate.search/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :spa.register/success
  [aether history app-state {:keys [noun] :as msg}]
  (let []
    (om/transact! app-state
                  (fn [app-state]
                    (assoc app-state
                           :user noun
                           :mode :spa.authenticated/online
                           :intent {:id :spa.authenticated/navigate}
                           :datum {:context-datums []
                                   :datum {}
                                   :similar-datums []}
                           :cache {}
                           :error-messages [])))
    (history/navigate-to! history :home)
    (->> (msg/initialize-client-state noun)
         (aether/send! aether))))

(defmethod local-response-handler :spa.register/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :spa.login/success
  [aether history app-state {:keys [noun] :as msg}]
  (let []
    (om/transact! app-state
                  (fn [app-state]
                    (assoc app-state
                           :user noun
                           :mode :spa.authenticated/online
                           :intent {:id :spa.authenticated/navigate}
                           :datum {:context-datums []
                                   :datum {}
                                   :similar-datums []}
                           :cache {}
                           :error-messages [])))
    (history/navigate-to! history :home)
    (->> (msg/initialize-client-state noun)
         (aether/send! aether))))

(defmethod local-response-handler :spa.login/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :spa.logout/success
  [aether history app-state msg]
  (let []
    (om/transact! app-state
                  (fn [app-state]
                    (assoc app-state
                           :user nil
                           :mode :spa.unauthenticated/online
                           :intent {:id :spa.unauthenticated/navigate}
                           :datum {:context-datums []
                                   :datum []
                                   :similar-datums []}
                           :error-messages []
                           :cache {})))
    (history/navigate-to! history :home)))

(defmethod local-response-handler :spa.logout/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :spa.state.initialize/success
  [aether history app-state msg]
  (let []
    (om/transact! app-state
                  (fn [app-state]
                    (merge app-state (get msg :noun))))))

(defmethod local-response-handler :spa.state.initialize/failure
  [aether history app-state msg]
  (record-error-message app-state msg))

(defmethod local-response-handler :spa.switch-team/success
  [aether history app-state msg]
  (let [user' (get msg :noun)]
    (om/transact! app-state (fn [app-state]
                              (assoc app-state :user (get msg :noun))))
    ;; NOTE: user changed, therefore creds changed, therefore must
    ;; reinitialize => could have a current-datum which is not
    ;; accessible from the new creds
    (->> (msg/initialize-client-state user')
         (aether/send! aether))
    (history/navigate-to! history :home)
    )
  )
(ns kanopi.controller.ajax
  (:require [quile.component :as component]
            [taoensso.timbre :as timbre
             :refer-macros (log trace debug info warn error fatal report)]
            [kanopi.aether.core :as aether]
            [kanopi.aether.spout :as spout]
            [cljs.core.async :as async]
            ))

(defrecord AjaxSpout [config spouts aether]
  component/Lifecycle
  (start [this]
    (if (not-empty spouts)
      this
      (let [
            ;;get-spout (->>
            ;;           (spout/new-http-spout )
            ;;           (component/start))
            ]
        ;; TODO: make a spout for api requests
        this)))
  
  (stop [this]
    (if-not (not-empty spouts)
      this
      (let []
        this))))

(defn new-ajax-spout [config]
  (map->AjaxSpout {:config config}))

(ns kanopi.view.widgets.dropdown
  "Generic dropdown widget."
  (:require-macros [cljs.core.async.macros :as asyncm])
  (:require [om.core :as om]
            [taoensso.timbre :as timbre
             :refer-macros (log trace debug info warn error fatal report)]
            [kanopi.util.browser :as browser]
            [sablono.core :refer-macros [html] :include-macros true]
            [kanopi.ether.core :as ether]
            [cljs.core.async :as async]
            [cljs-uuid-utils.core :as uuid]
            [cljs-time.core :as t]))

(defn- open-dropdown! [owner]
  (om/set-state! owner :expanded true))

(defn- close-dropdown! [owner]
  (om/set-state! owner :expanded false))

(defn- start-hover! [owner]
  (let [kill-hover-clock (async/chan 1)]
    (om/set-state! owner ::kill-hover-clock-ch kill-hover-clock)
    (asyncm/go
     (let [[v ch] (async/alts! [kill-hover-clock (async/timeout 1000)])]
       ;; NOTE: non-nil value must be sent to kill-channel
       (when (nil? v)
         (om/set-state! owner :expanded true))
       (async/close! kill-hover-clock)))))

(defn- stop-hover! [owner]
  (async/put! (om/get-state owner ::kill-hover-clock-ch) :stop!))

(defmulti dropdown-menu-item :type)

(defmethod dropdown-menu-item :link
  [itm]
  [:li [:a {:href (get itm :href)} (get itm :label)]])

(defmethod dropdown-menu-item :divider
  [itm]
  [:li.divider {:role "separator"}])

;; NOTE: amazon expands the dropdown when user hovers for
;; a few seconds. it's awesome.
(defn dropdown
  "All configuration is passed via local state."
  [props owner opts]
  (reify
    om/IInitState
    (init-state [_]
      ;; defaults
      {:toggle-label "Toggle label"
       :menu-items [{:type :link
                     :href ""
                     :label "Item 1"}
                    {:type :divider}
                    {:type :link
                     :href ""
                     :label "Item 2"}]
       :expanded nil
       ;; internal stuff
       ::when-hover-started nil})

    om/IRenderState
    (render-state [_ {:keys [expanded] :as state}]
      (let []
        (html
         [:li.dropdown
          [:a.dropdown-toggle
           {:on-click #(open-dropdown! owner)
            :data-toggle "dropdown"
            :role "button"
            :aria-haspopup "true"
            :aria-expanded expanded
            :on-mouse-enter #(start-hover! owner)
            :on-mouse-leave #(stop-hover! owner)}
           (str (get state :toggle-label) " ") [:span.caret]]
          (into
           [:ul.dropdown-menu
            {:style {:display (when expanded "inherit")}
             :on-mouse-leave #(close-dropdown! owner)}]
           (map dropdown-menu-item (get state :menu-items)))
          ])))))
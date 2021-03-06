(ns kanopi.view.widgets.input-field
  (:require [om.core :as om]
            [taoensso.timbre :as timbre
             :refer-macros (log trace debug info warn error fatal report)]
            [kanopi.view.icons :as icons]
            [kanopi.util.browser :as browser]
            [sablono.core :refer-macros (html) :include-macros true]
            ))

(defn- start-edit [e owner korks]
  (om/set-state! owner korks true))

(defn- handle-change [e owner korks]
  (om/set-state! owner korks (.. e -target -value)))

(defn- end-edit
  ([e owner handler-fn]
   (end-edit e owner nil handler-fn))

  ([e owner editing-state-korks handler-fn]
   (when editing-state-korks
     (om/set-state! owner editing-state-korks false)) 
   (handler-fn (.. e -target -value))
   (om/set-state! owner :new-value nil)))

(defn editable-value
  "Required initial state:
     edit-key for accessing value to be edited from props
     submit-value
  "
  [props owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:editing false
       :edit-icon-enabled true
       :hovering false
       :default-value "Input field default value"})

    om/IDidUpdate
    (did-update [_ prev-props prev-state]
      ;; focus on text field when editing an input field
      (when (and (not (get prev-state :editing))
                 (om/get-state owner :editing))
       (. (om/get-node owner "text-field") (focus))))

    om/IRenderState
    (render-state [_ {:keys [editing edit-key submit-value hovering default-value]
                      :as state}]
      (let [current-value (get props edit-key)]
        (html
         [:span.editable-text-container
          {:style {:border-bottom-color (when editing "green")}}
          ; NOTE: this design is problematic.
          ; the user cannot user 'TAB' to navigate to the input field
          ; because it is not in the DOM when the user presses that
          ; button. is there a way to just use an INPUT and heavily
          ; alter its interactions when not 'editing'?
          [:span.view-editable-text
             {:style {
                      :display (when editing "none")
                      }
              :class [(when hovering "bold-text")
                      (when (empty? current-value) "placeholder")]
              :on-click #(start-edit % owner :editing)}
             (if (not-empty current-value)
               current-value
               default-value)]

          [:input.edit-editable-text
           {:style       {
                          :display (when-not editing "none")
                          }
            :ref         "text-field"
            :type        "text"
            :value       (or (get state :new-value) current-value)
            :tab-index   (get state :tab-index)
            :placeholder (get state :placeholder)
            ; :on-focus    #(println "focus input")
            :on-change   #(handle-change % owner :new-value)
            :on-key-down #(when (= (.-key %) "Enter")
                            ;; NOTE: this triggers on-blur, which
                            ;; calls the submit-value handler fn.
                            (om/set-state! owner :editing false))
            :on-blur     #(end-edit % owner :editing submit-value)}]

          ])))))

(defn integer [props owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:on-submit (constantly nil)
       :placeholder nil
       :tab-index 0
       :new-value nil
       })
    
    om/IRenderState
    (render-state [_ {:keys [on-submit] :as state}]
      (html
       [:input.validated
        {:type "number" :step "1"
         :value       (or (get state :new-value) (get state :initial-input-value))
         :on-change   #(handle-change % owner :new-value)
         :tab-index   (get state :tab-index)
         :placeholder (get state :placeholder)
         :on-key-down #(when (= (.-key %) "Enter")
                         (.blur (.-target %)))
         :on-blur     #(end-edit % owner on-submit)
         ; TODO: get pattern from kanopi.model.schema/literal-types
         :pattern     "^[0-9]*$"}]))))

(defn decimal [props owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:on-submit (constantly nil)
       :placeholder nil
       :tab-index 0
       :new-value nil
       })
    om/IRenderState
    (render-state [_ {:keys [on-submit] :as state}]
      (html
       [:input.validated
        {
         :type "number" :step "any"
         :value       (or (get state :new-value) (get state :initial-input-value))
         :on-change   #(handle-change % owner :new-value)
         :tab-index   (get state :tab-index)
         :placeholder (get state :placeholder)
         :on-key-down #(when (= (.-key %) "Enter")
                         (.blur (.-target %)))
         :on-blur     #(end-edit % owner on-submit)
         :pattern     "^[0-9]?[0-9]*.?+[0-9]*$"}]
       ))))

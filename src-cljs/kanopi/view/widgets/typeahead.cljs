(ns kanopi.view.widgets.typeahead
  "What do I want from typeahead search?

  A fair amount, apparently.
  "
  (:require-macros [cljs.core.async.macros :as asyncm])
  (:require [om.core :as om]
            [taoensso.timbre :as timbre
             :refer-macros (log trace debug info warn error fatal report)]
            [sablono.core :refer-macros [html] :include-macros true]
            [cljs.core.async :as async]
            [goog.dom :as gdom]
            [kanopi.model.message :as msg]
            [kanopi.model.schema :as schema]
            [kanopi.view.widgets.selector.dropdown :as dropdown]
            [kanopi.util.browser :as browser]
            [kanopi.util.async :as async-util]
            [kanopi.util.core :as util]
            [kanopi.aether.core :as aether]))

(defn- handle-result-click
  [owner res evt]
  (om/update-state!
   owner
   (fn [state]
     (println "HANDLE_RESULT_CLICK" (schema/get-value res))
     (assoc state
            :focused false
            :input-value (if (get state :clear-on-click)
                           nil
                           (schema/get-value res)))))
  ;; href-fn always exists, but should only be used when it
  ;; produces a truthy value. by default it always evaluates to
  ;; nil.
  (if-let [href ((om/get-state owner :href-fn) res)]
    (browser/set-page! owner href)
    ((om/get-state owner :on-click) res evt)))

(defn- handle-submission [owner evt]
  (let [{submit-fn :on-submit value :input-value}
        (om/get-state owner)]
    (submit-fn value evt)
    (om/set-state! owner :focused false)))

(defn- handle-key-down
  [owner search-results evt]
 
  (case (.-key evt)

    "ArrowDown"
    (do (om/update-state! owner :selection-index
                          (fn [x]
                            (if (< x (dec (count search-results)))
                              (inc x)
                              x)))
        (. evt preventDefault))

    "ArrowUp"
    (do (om/update-state! owner :selection-index
                          (fn [x]
                            (if (> x 0)
                              (dec x)
                              x)))
        (. evt preventDefault))

    ("Enter" "Tab")
    (let [[idx selected-result]
          (nth search-results (om/get-state owner :selection-index) nil)
          ]
      (. evt preventDefault)

      (if selected-result
        (do
         ; NOTE: this should only happen when user is not editing the typeahead
         (handle-result-click owner selected-result evt) 
         ;; href-fn always exists, but should only be used when it
         ;; produces a truthy value. by default it always evaluates to
         ;; nil.
         (when-let [href ((om/get-state owner :href-fn) selected-result) ]
           (browser/set-page! owner href))

         ;; on-click fn is side-effecting, so it may always be called
         ;; even if its default is used, which always evaluates to nil
         ((om/get-state owner :on-click) selected-result evt))
        (do
         (handle-submission owner evt)) )

      (.. evt -target (blur)))

    "Escape"
    (do
     (. evt preventDefault)
     (om/set-state! owner :focused false)
     (om/update-state! owner #(assoc % :input-value (:initial-input-value %)))
     (.. evt -target (blur)))

    ;; default
    nil))


(defn- element-specific-attrs
  [{:keys [element-type] :as state}]
  (case element-type
    :input
    (hash-map :type "text")

    :textarea
    (hash-map :cols 32, :rows 3)

    ;; default
    {}))

(defn handle-typeahead-input [owner evt]
  (let [v (.. evt -target -value)]
    (async/put! (om/get-state owner :input-ch) (msg/search v))
    ((om/get-state owner :on-change) v)
    (om/set-state! owner :input-value v)))

(defn typeahead
  "Pre-load with local or cached results.

  Supports different element types.

  NOTE: everything this component does besides take input and return
  matching search reuslts is configurable by passing functions in as
  initial state.
  "
  [props owner opts]
  (reify
    om/IInitState
    (init-state [_]
      {:element-type :input ;; supported values are #{:input :textarea}
       :tab-index 0 ;; decided by platform convention by default
       :clear-on-click false ;; used in `handle-result-click'

       ;; Used to render search results into strings for display.
       :display-fn str
       ;; What to display when there are no search results.

       ;; FIXME: use data of the right shape here.
       :empty-result [[nil "No matching entities. Hit Enter to create one."]]

       ;; Escape hatch for tracking the input field's value.
       :on-change (constantly nil)

       ;; Outside world can track this too!
       ;; Fns take 1 arg: the current input value.
       :on-focus (constantly nil)
       :on-blur  (constantly nil)
       ;; Submission is different from selection (below).
       :on-submit (constantly nil)

       ;; The Selection Handlers.
       ;; NOTE: on-click is side-effecting
       :on-click (constantly nil)
       ;; NOTE: href-fn is a pure function which returns a url path
       :href-fn (constantly nil)
       ;; SUMMARY: these 2 are different because their usage in web
       ;; browsers is different. we're just sticking to that. when
       ;; href's value is nil the browser ignores it. 

       ;; INTERNAL
       ;; TODO: use namespaced keywords for internal stuff
       ;; FIXME: debounce is not working...or is it?
       :input-ch (-> (async/chan)
                     (async-util/debounce 100)
                     (async/pipe (msg/publisher owner)))
       :selection-index -1
       :focused false
       ; initial-input-value should be set externally
       :initial-input-value nil
       ; input-value should be kept internal
       ; NOTE: currently being overridden by some users so they can
       ; control state.
       :input-value nil
       :placeholder "Placeholder"
       })

    ;; NOTE: this plus the stopPropagation beyond the input element
    ;; below are an extremely ugly hack to make it so clicking outside
    ;; the typeahead removes focus, while clicking inside works
    ;; without causing the field to blur.
    ;;
    ;; The problem: clicking items in the results dropdown menu.
    ;; Those items would cause the input field to blur, in blur we'd
    ;; remove focus from the input field, and then the click would not
    ;; propagate. Blurs just don't propagate other events. It sucks.
    ;;
    ;; TODO: refactor with closure event lib
    ;; FUCK: this breaks on page changes because the header unmounts
    ;; and remounts, but not always in the right order or owner
    ;; somehow changes.
    ;; Terrible solution: set window.onclick on every render :(
    om/IWillUnmount
    (will-unmount [_]
      (set! js/window.onclick nil))

    om/IRenderState
    (render-state [_ {:keys [focused input-ch input-value display-fn
                             on-focus on-blur on-change href-fn
                             ]
                      :as state}]
      ;; NOTE: see NOTE above om/IWillMount for explanation
      (set! js/window.onclick (fn [_]
                                (on-blur (get state :input-value))
                                (om/set-state! owner :focused false)))
      (let [all-search-results (om/observe owner ((om/get-shared owner :search-results)))
            search-results (get all-search-results input-value (get state :empty-result []))
            ]
        (html
         [:div.typeahead
          ;; NOTE: see NOTE above om/IWillMount for explanation
          {:on-click (fn [evt]
                       (. evt stopPropagation))}
          (vector
           ;; NOTE: this is insane. It may be better to have separate
           ;; blocks for each element type, though that would lead to
           ;; more duplicated code.
           (get state :element-type)
           (merge (element-specific-attrs state)
                  {:class (concat (get state :classes []) [])
                   :on-focus    (fn [_]
                                  (om/set-state! owner :focused true)
                                  (on-focus (get state :input-value))) 
                   ; NOTE: see notes above. faking on-blur with a
                   ; window on-click event.
                   ; :on-blur (fn [_]
                   ;            (on-blur (get state :input-value))
                   ;            (om/set-state! owner :focused false))
                   :tab-index (get state :tab-index)
                   :value       (or (get state :input-value)
                                    (get state :initial-input-value))
                   :placeholder (get state :placeholder)
                   :style {:border-bottom-color
                           (when (om/get-state owner :focused) "green")
                           }
                   :on-change   (partial handle-typeahead-input owner) 
                   :on-key-down (partial handle-key-down owner search-results)
                   }))
          [:ul.dropdown-menu.typeahead-results
           {:style {:display (if (and (om/get-state owner :focused)
                                      (not-empty input-value)
                                      (not-empty search-results))
                               "inherit"
                               "none")}}
           (for [[idx [score res]] (map-indexed vector search-results)]
             [:li.dropdown-menu-item
              (when (= idx (get state :selection-index))
                [:span.dropdown-menu-item-marker])
              [:a {:on-click (partial handle-result-click owner res)}
               [:span (display-fn res)]]])]
          ])))))

(def ^:private search-required-args
  [:result-display-fn :result-href-fn :result-on-click])

(def ^:private search-optional-args
  [:tab-index :placeholder])

(defn search-config*
  [required-args optional-args]
  (let [{:keys [result-display-fn
                result-href-fn
                result-on-click]}
        required-args
        ]
    (assert (some fn? [result-href-fn result-on-click])
            "At least one result click handler must be provided.")
    (hash-map
     :init-state (merge optional-args
                        (cond-> {:display-fn result-display-fn
                                 :clear-on-click true}
                          result-href-fn
                          (assoc :href-fn  result-href-fn)
                          result-on-click
                          (assoc :on-click result-on-click))
                        ))))

(defn search-config
  "State constructor fn for the set of knobs required for using the
  typeahead widget as a search field. The value of the input field is
  generally ignored, instead, result selection is the goal."
  ([& args]
   (let [argmap (apply hash-map args)]
     (search-config* (select-keys argmap search-required-args)
                     (select-keys argmap search-optional-args)))))

(def ^:private editor-required-args
  [:element-type :input-value
   :input-on-change :input-on-blur :input-on-focus
   :result-display-fn :result-on-click :on-submit 
   ])
(def ^:private editor-optional-args
  [:tab-index :placeholder])

(defn editor-config*
  [required-args optional-args]
  (let [{:keys [on-submit
                input-value
                input-on-change
                input-on-blur
                input-on-focus
                result-display-fn
                result-on-click
                element-type]}
        required-args]
    (assert (every? fn? [input-on-change input-on-blur input-on-focus
                         result-display-fn result-on-click on-submit])
            "Missing required functions.")
    (assert (contains? #{:input :typeahead} element-type))
    (hash-map
     :state      {:input-value input-value
                  }
     :init-state (merge optional-args
                        {:element-type        element-type
                         :initial-input-value input-value
                         :clear-on-click      false

                         :on-focus   input-on-focus
                         :on-blur    input-on-blur
                         :on-change  input-on-change

                         :display-fn result-display-fn
                         :on-click   result-on-click

                         :on-submit  on-submit
                         }) )))

(defn editor-config
  "State constructor fn for the knobs required when using the
  typeahead widget as a fancy editor for user data. Generally this
  means any use-case where the value of the input field matters at all
  times. Result selection only matters as far as it is used to change
  the value of the input field. The primary goal is to help the user
  modify the value."
  ([& args]
   (let [argmap (apply hash-map args)]
     (editor-config* (select-keys argmap editor-required-args)
                     (select-keys argmap editor-optional-args)))))

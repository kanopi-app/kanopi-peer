(ns kanopi.view.resources.templates
  (:require [hiccup.page :refer (html5 include-js include-css)]
            [liberator.representation :as rep]
            [cheshire.core :as json]
            [environ.core :refer (env)]
            [kanopi.util.core :as util]))

(defn include-bootstrap []
  (include-css "/css/bootstrap.3.3.5.min.css"))

(defn include-spa [dev]
  (include-js (if dev
                "/js/main.js"
                "/js/main_prod.js")))

(defn include-data [plain-text-data]
  [:script
   {:type "text/plain"
    :id "kanopi-init"}
   plain-text-data])

(defn header [title]
  (vector :head
          [:title title]
          [:link {:rel "icon"
                  :type "image/png"
                  :href "/favicon.png"}]
          (include-css "/css/main.css")
          (include-bootstrap)
          ; Katex
          (include-js "//cdnjs.cloudflare.com/ajax/libs/KaTeX/0.5.1/katex.min.js")
          (include-css "//cdnjs.cloudflare.com/ajax/libs/KaTeX/0.5.1/katex.min.css")

          ; CodeMirror
          ; This is not how I want to pull in JS/CSS dependencies.
          ; TODO: figure out a better way. CLJS/JS does not work with
          ; CSS when using Leiningen.
          (include-css "https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.8.0/codemirror.min.css")
          
          ))

(defn om-page
  "TODO: set cookie with no expiration (expire at end of session)
  which contains user identity and recently modified datums and config information"
  [ctx {:keys [title session-state] :as opts}]
  (let [cookies (get-in ctx [:request :cookies])]
    (rep/ring-response
     {:cookies cookies

      :body
      (html5
       (header title)
       [:body
        (when session-state
          (include-data (util/transit-write session-state))) 
        [:div#app-container]
        (include-spa (env :dev))])
      })))

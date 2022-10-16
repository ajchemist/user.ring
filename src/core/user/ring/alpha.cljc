(ns user.ring.alpha
  (:require
   [clojure.string :as str]
   #?@(:clj
       [[ring.util.request :as request]
        [user.ring.alpha.util :as util]]
       )))


(defn path-info
  [#?(:clj request :default _)]
  #?(:clj (request/path-info request)
     :cljs js/location.pathname))


;; * fns


(defn index-request?
  [request]
  (let [path (path-info request)]
    (or (str/ends-with? path "/")
        (str/ends-with? path "/index.html")
        (str/ends-with? path "/index.htm"))))


;; * routing


(defn routing
  [request handlers]
  (some (fn [handler] (handler request)) handlers))


(defn routes
  [& handlers]
  #(routing % handlers))


;;


;; * transform


(defn wrap-transform-request
  {:style/indent [:defn]}
  ([handler f]
   {:pre [(ifn? f)]}
   (fn
     ([request] (handler (f request)))
     ([request respond raise] (handler (f request) respond raise)))))


(defn wrap-pre-transform-response
  {:style/indent [:defn]}
  ([handler f]
   {:pre [(ifn? f)]}
   (fn
     ([request] (f (handler request)))
     ([request respond raise] (handler request #(respond (f %)) raise)))))


(defn wrap-post-transform-response
  {:style/indent [:defn]}
  ([handler f]
   {:pre [(ifn? f)]}
   (fn
     ([request] (-> request handler f))
     ([request respond raise] (handler request #(f (respond %)) raise)))))


(def ^{:arglists '([handler f]) :style/indent [:defn]}
  wrap-transform-response wrap-pre-transform-response)


(comment
  (defmacro with-transform-request
    "`body` must return request map"
    {:style/indent [:defn]}
    [bind & body]
    `(fn [next-handler#]
       (fn
         ([request#]
          (let [~bind   request#
                result# (do ~@body)]
            (next-handler# result#)))
         ([request# respond# raise#]
          (let [~bind   request#
                result# (do ~@body)]
            (next-handler# result# respond# raise#))))))


  (defmacro with-transform-response
    "`body` must return response map"
    {:style/indent [:defn]}
    [bind & body]
    `(fn [next-handler#]
       (fn
         ([request#]
          (let [result# (next-handler# request#)
                ~bind   result#]
            ~@body))
         ([request# respond# raise#]
          (let [result# (next-handler# request# respond# raise#)
                ~bind   result#]
            ~@body)))))
  )


(defn wrap-transform-response-2
  "`f` should have 2 args: [request response]

  `f` should return ring compatible http `response`"
  {:style/indent [:defn]}
  [handler f]
  {:pre [(fn? f)]}
  (fn
    ([req]
     (f req (handler req)))
    ([req respond raise]
     (handler req #(respond (f req %)) raise))))


;; * file-or-resource


(def ^:dynamic *context-dir* nil)


(defn wrap-context-dir
  [handler context-dir]
  (fn
    ([request]
     (binding [*context-dir* context-dir]
       (handler request)))
    ([request respond raise]
     (binding [*context-dir* context-dir]
       (handler request respond raise)))))


#?(:clj
   (defn file
     [path]
     (util/file *context-dir* path)))


#?(:clj
   (defn file-or-resource
     [path]
     (util/file-or-resource *context-dir* path)))


#?(:clj
   (defn path-component
     [request component-name]
     (let [path        (path-info request)
           config-path (if (str/ends-with? path "/")
                         (str path component-name)
                         (str path "." component-name))]
       (file-or-resource config-path))))


#?(:clj
   (defn wrap-path-component
     [handler ext-param-key component-name read-component]
     {:pre [(keyword? ext-param-key) (fn? read-component)]}
     (wrap-transform-request
       handler
       (fn [request]
         (try
           (let [component (path-component request component-name)]
             (cond-> request
               (some? component)
               (assoc ext-param-key (read-component component))))
           (catch Exception _
             request))))))


;; * meta


#?(:clj
   (defn wrap-meta-response
     [handler]
     (letfn [(f [{:keys [body] :as response}]
               (if (instance? clojure.lang.IMeta body)
                 (with-meta body (dissoc response :body))
                 response))]
       (fn
         ([request]
          (f (handler request)))
         ([request respond raise]
          (handler request #(respond (f %)) raise))))))


;; * content-type


(defn wrap-content-type
  [handler content-type]
  {:pre [(string? content-type)]}
  (wrap-transform-request handler
    (fn [request]
      (update request :content-type #(or % content-type)))))


(defn content-type
  "Returns the value of the Content-Type header of `request`."
  [{:keys [headers]}]
  (let [content-type (get headers "content-type")]
    (when-not (str/blank? content-type)
      (keyword (str/replace content-type #";.*" "")))))

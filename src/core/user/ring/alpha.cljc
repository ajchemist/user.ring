(ns user.ring.alpha
  (:require
   [clojure.string :as str]
   #?@(:clj
       [[ring.util.request :as request]
        [user.ring.alpha.util :as util]]
       )))


;; * request property


(defn path-info
  [#?(:clj request :default _)]
  #?(:clj (request/path-info request)
     :cljs js/location.pathname))


(defn scheme
  [request]
  (or (get-in request [:headers "x-scheme"])
      (get-in request [:headers "x-forwarded-proto"])
      (name (:scheme request))))


(defn origin-url
  [request]
  (str (scheme request) "://" (get-in request [:headers "host"])))


(defn request-url
  "Custom ring.util.request/request-url
  Respect X-Scheme, X-Forwarded-Proto"
  [request]
  (str (origin-url request)
       (:uri request)
       (when-let [query (:query-string request)]
         (str "?" query))))


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
  {:pre [(string? context-dir)]}
  (fn
    ([request]
     (binding [*context-dir* context-dir]
       (handler request)))
    ([request respond raise]
     (binding [*context-dir* context-dir]
       (handler request respond raise)))))


#?(:clj
   (defn file
     ([path]
      (file *context-dir* path))
     ([root-dir path]
      (util/file root-dir path))))


#?(:clj
   (defn file-or-resource
     ([path]
      (file-or-resource *context-dir* path))
     ([root-dir path]
      (util/file-or-resource root-dir path))))


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
     [handler ext-param-key component-name read-component & [nf]]
     {:pre [(keyword? ext-param-key) (fn? read-component)]}
     (wrap-transform-request
       handler
       (fn [request]
         (try
           (cond
             (::path-component? request)
             (let [component (or (path-component request component-name) nf)]
               (cond-> request
                 (some? component)
                 (assoc
                   ::path-component component
                   ext-param-key (read-component component))))


             :else
             request)
           (catch Exception e
             (tap> [::wrap-path-component e])
             request))))))


;; * meta


#?(:clj
   (defn wrap-meta-response
     [handler]
     (letfn [(f [{:keys [body] :as response}]
               (if (instance? clojure.lang.IMeta body)
                 (vary-meta body merge (dissoc response :body))
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

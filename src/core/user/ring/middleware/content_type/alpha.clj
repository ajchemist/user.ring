(ns user.ring.middleware.content-type.alpha
  "Slightly different from `ring.middleware.content-type`"
  (:require
   [ring.util.mime-type :as mime]
   [ring.util.response :as response]
   ))


(defn content-type-response
  ([response request]
   (content-type-response response request {}))
  ([response {:keys [uri]} options]
   (when response
     (if (response/get-header response "Content-Type")
       response
       ;; this is brought from
       ;; `compojure.route/add-mime-type`
       (if-let [mime-type (mime/ext-mime-type uri (:mime-types options {}))]
         (response/content-type response mime-type)
         response)))))


(defn wrap-content-type
  {:style/indent [:defn]}
  ([handler]
   (wrap-content-type handler {}))
  ([handler options]
   (fn
     ([request]
      (content-type-response (handler request) request options))
     ([request respond raise]
      (handler
        request
        #(content-type-response (respond %) request options)
        raise)))))

(ns user.ring.alpha-test
  (:require
   [clojure.test :as test :refer [deftest testing is are]]
   [clojure.string :as str]
   [taoensso.timbre :as timbre]
   [clj-http.core :as http.core]
   [clj-http.client :as http]
   [ring.util.codec :refer [form-encode form-decode]]

   ;; application/json
   [cheshire.core :as cheshire]

   [user.ring.alpha :refer :all]
   [ring.util.response :as response]

   [ring.util.request :as request]))


(deftest file-or-resource-test
  (is (nil? (file "/test/assets/index.html")))
  (is (some? (file-or-resource "/test/assets/index.html")))
  (is (instance? java.io.File (binding [*context-dir* "src/test"] (file "/test/assets/index.html"))))
  (is (instance? java.io.File
                 (:body ((-> (fn [request] (response/response (path-component request "index.html")))
                           (wrap-context-dir "src/test"))
                         {:uri "/test/assets/" :request-method :get}))))
  (is (instance? java.net.URL
                 (:body ((fn [request] (response/response (path-component request "index.html")))
                         {:uri "/test/assets/" :request-method :get})))))


(deftest test-wrap-meta-response
  (is
   (map?
    ((-> http.core/request
       (http/wrap-request)
       (wrap-meta-response)      ; string is not a instance of clojure.lang.IMeta
       ((fn [client]
          (fn
            ;; 문제 없음
            ([req]
             (client (assoc req :content-type "application/json")))
            ([req respond raise]
             (-> req
               (assoc :content-type "application/json")
               (client respond raise)))))))
     { ;; :async? true
      :method :get
      :url "https://crix-api.upbit.com/v1/crix/trades/ticks"
      :query-params
      {:code "CRIX.UPBIT.KRW-BTC"
       :count 50}})))

  (let [result ((-> http.core/request
                  (http/wrap-request)
                  (wrap-meta-response) ; InputStream is not a instance of clojure.lang.IMeta
                  ((fn [client]
                     (fn self
                       ;; 문제 있음
                       ([req]
                        (self req nil nil))
                       ([req respond raise]
                        (-> req
                          (assoc :content-type "application/json")
                          (client respond raise)))))))
                { ;; :async? true
                 :method :get
                 :url "https://crix-api.upbit.com/v1/crix/trades/ticks"
                 :query-params
                 {:code "CRIX.UPBIT.KRW-BTC"
                  :count 50}})]
    (is (map? result))
    (is (instance? java.io.InputStream (:body result))))
  )


(def ^{:arglists     '([request] [request respond raise])
       :style/indent [0]}
  json-client
  (-> http.core/request
    (http/wrap-request)
    (wrap-transform-response
      (fn [response]
        (update response :body #(cheshire/decode % true))))

    ;; post-wrap
    (wrap-meta-response)

    ;; pre-wrap
    (wrap-content-type "application/json")
    ))


(comment

  (json-client
   {;; :async? true
    :method :get
    :url "https://crix-api-endpoint.upbit.com/v1/crix/trades/ticks"
    :query-params
    {:code "CRIX.UPBIT.KRW-BTC"
     :count 50}}
   )

  ((-> http/request
     (wrap-content-type "application/json"))
   {:async? true
    :method :get
    :url "https://crix-api-endpoint.upbit.com/v1/crix/trades/ticks"
    :query-params
    {:code "CRIX.UPBIT.KRW-BTC"
     :count 50}}
   (-> identity
     (wrap-transform-response
      (fn [response]
        (deserialize-body response #(cheshire/decode % true))))
     (wrap-meta-response)
     (wrap-transform-response println))
   #_(fn [response]
       (println (:body (deserialize-body response #(cheshire/decode % true)))))
   (fn [e] (timbre/error e))
   ))

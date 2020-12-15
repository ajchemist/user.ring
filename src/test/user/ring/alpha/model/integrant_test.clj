(ns user.ring.alpha.model.integrant-test
  (:require
   [clojure.test :as test :refer [deftest is are testing]]
   [integrant.core :as ig]
   [user.ring.alpha.model.integrant :as ig.ring]
   [ring.util.response :as response]
   [clj-http.client :as http]
   ))


(def system
  (ig/init
    {::ig.ring/jetty-server
     {:handler (constantly (response/response "Hello, world!"))
      :options {:port 8080 :join? false}}}))


(deftest main
  (prn (:body (http/get "http://localhost:8080/")))
  (ig/halt! system))

(ns user.ring.alpha.model.integrant
  (:require
   [integrant.core :as ig]
   [ring.adapter.jetty :as jetty]
   ))


(set! *warn-on-reflection* true)


(defmethod ig/init-key ::jetty-server
  [_ {:keys [handler options]}]
  (let [server (jetty/run-jetty handler options)
        host   (.. server getURI getHost)
        port   (.. server getURI getPort)]
    (println (str "jetty server started on: http://" host ":" port))
    server))


(defmethod ig/halt-key! ::jetty-server
  [_ ^org.eclipse.jetty.server.Server server]
  (println "Stopping jetty server...")
  (.stop server))


(set! *warn-on-reflection* false)

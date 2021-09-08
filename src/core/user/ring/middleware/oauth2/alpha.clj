(ns user.ring.middleware.oauth2.alpha
  (:require
   [clojure.string :as str]
   [clj-http.client :as http]
   [ring.util.codec :as codec]
   [ring.util.request :as request]
   [ring.util.response :as resp]
   [crypto.random :as random]
   )
  (:import
   java.util.Date
   java.time.Instant
   ))


(defn str-contain?
  [^String s contain]
  (.contains s contain))


;;


(defn random-state
  []
  (random/url-part 9))


(defn state-matches?
  [request]
  (= (get-in request [:session ::state])
     (get-in request [:query-params "state"])))


;;


(defn- parse-redirect-uri
  [{:keys [redirect-uri]}]
  (.getPath (java.net.URI/create redirect-uri)))


(defn- redirect-uri
  [profile request]
  (-> (request/request-url request)
      (java.net.URI/create)
      (.resolve (:redirect-uri profile))
      str))


;;


(defn- authorize-uri
  [profile request state]
  (str
    (:authorize-uri profile)
    (if (str-contain? (:authorize-uri profile) "?") "&" "?")
    (codec/form-encode
      (cond-> {:response_type (:authorize-response-type profile "code")
               :client_id     (:client-id profile)
               :state         state}
        (:redirect-uri profile) (assoc :redirect_uri (redirect-uri profile request))
        (:scope profile)        (assoc :scope (str/join " " (map name (:scope profile))))))))


;;


(defn- add-header-credentials
  [request id secret]
  (assoc request :basic-auth [id secret]))


(defn- add-form-credentials
  [{:as request} id secret]
  (update request :form-params
    merge
    {:client_id     id
     :client_secret secret}))


(defn- format-access-token
  [{{:keys [access_token expires_in refresh_token id_token] :as body} :body}]
  (cond-> {:_raw body}
    access_token  (assoc :token access_token)
    expires_in    (assoc :expires (Date/from (.plusSeconds (Instant/now) (long expires_in))))
    refresh_token (assoc :refresh-token refresh_token)
    id_token      (assoc :id-token id_token)))


(defn- get-access-token
  [{:keys [access-token-uri client-id client-secret basic-auth?]
    :or   {basic-auth? false}
    :as   profile} request]
  (format-access-token
    (http/request
      (cond-> {:method      :post
               :url         access-token-uri
               :accept      :json
               :as          :json
               :form-params {:grant_type    "authorization_code"
                             :code          (get-in request [:query-params "code"])
                             :redirect_uri  (redirect-uri profile request)}}
        basic-auth?       (add-header-credentials client-id client-secret)
        (not basic-auth?) (add-form-credentials client-id client-secret)))))


;;


(defn- make-launch-handler
  [profile]
  (fn [{:as request}]
    (let [state (random-state)]
      (-> (resp/redirect (authorize-uri profile request state))
        (assoc-in [:session ::state] state)))))


;;


(defn state-mismatch-handler
  [_]
  {:status 400, :headers {}, :body "State mismatch"})


(defn- make-redirect-handler
  [{:keys [id landing-uri] :as profile}]
  (let [state-handler (:state-mismatch-handler profile state-mismatch-handler)]
    (fn [{:as request}]
      (if (state-matches? request)
        (let [access-token (get-access-token profile request)]
          (-> (resp/redirect landing-uri)
            (update :session
              #(-> %
                 (assoc-in [::access-tokens id] access-token)
                 (dissoc ::state)))))
        (state-handler request)))))


;;


(defn- assoc-access-tokens
  [request]
  (if-let [tokens (get-in request [:session ::access-tokens])]
    (assoc request ::access-tokens tokens)
    request))


(defn wrap-oauth2
  [handler profiles]
  (let [profiles  (for [[k v] profiles] (assoc v :id k))
        launches  (into {} (map (juxt :launch-uri identity)) profiles)
        redirects (into {} (map (juxt parse-redirect-uri identity)) profiles)]
    (fn
      [{:keys [uri] :as request}]
      (if-let [profile (launches uri)]
        ((make-launch-handler profile) request)
        (if-let [profile (redirects uri)]
          ((make-redirect-handler profile) request)
          (handler (assoc-access-tokens request)))))))

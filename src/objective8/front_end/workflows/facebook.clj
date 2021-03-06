(ns objective8.front-end.workflows.facebook
  (:require [ring.util.response :as response]
            [objective8.utils :as utils]
            [objective8.front-end.api.http :as http]
            [cheshire.core :as json]
            [bidi.ring :refer [make-handler]]
            [objective8.front-end.front-end-requests :as front-end]
            [clojure.tools.logging :as log]))

(def redirect-uri (str utils/host-url "/facebook-callback"))
(def error-redirect (response/redirect (str utils/host-url "/error/log-in")))

(defn facebook-sign-in [{:keys [facebook-config] :as request}]
  (let [client-id (:client-id facebook-config)]
    (response/redirect (str "https://www.facebook.com/dialog/oauth?client_id=" client-id
                            "&redirect_uri=" redirect-uri
                            "&scope=public_profile,email"))))

(defn response->json [response]
  (json/parse-string (:body response) true))

(defn token-info-valid? [token-info facebook-config]
  (let [expires-at (:expires_at token-info)
        app-id (:app_id token-info)
        client-id (:client-id facebook-config)]
    (and (= app-id client-id) (> expires-at (utils/unix-current-time)))))

(defn get-token-info [access-token facebook-config]
  (let [client-id (:client-id facebook-config)
        client-secret (:client-secret facebook-config)]
    (http/get-request "https://graph.facebook.com/debug_token" {:query-params {:input_token  access-token
                                                                               :access_token (str client-id "|" client-secret)}})))

(defn get-access-token [{:keys [params facebook-config] :as request}]
  (let [code (:code params)
        client-id (:client-id facebook-config)
        client-secret (:client-secret facebook-config)]
    (http/get-request "https://graph.facebook.com/v2.3/oauth/access_token" {:query-params {:client_id     client-id
                                                                                           :redirect_uri  redirect-uri
                                                                                           :client_secret client-secret
                                                                                           :code          code}})))

(defn get-user-email [user-id]
  (http/get-request (str "https://graph.facebook.com/v2.5/" user-id "/?fields=email")))

(defn check-fb-email [{:keys [session] :as request} user-id]
  (let [fb-user-id (str "facebook-" user-id)
        user-email-response (get-user-email user-id)
        user-email-body (response->json user-email-response)
        fb-user-email (:email user-email-body)
        session-with-auth-id {:session (assoc session :auth-provider-user-id fb-user-id)}
        redirect (into (response/redirect (str utils/host-url "/sign-up"))
                       session-with-auth-id)]
    (if (front-end/valid-not-empty-email? fb-user-email)
      (into redirect
            (assoc-in session-with-auth-id [:session :auth-provider-user-email] fb-user-email))
      (do (log/error (str "Facebook email error: " (get-in user-email-body [:error :message]) " with status code " (:status user-email-response)))
          (into redirect {:flash {:validation :auth-email}})))))

(defn check-token-info [{:keys [facebook-config] :as request} access-token]
  (let [token-info-response (get-token-info access-token facebook-config)
        token-info-body (response->json token-info-response)
        token-info-data (:data token-info-body)
        user-id (:user_id token-info-data)]
    (if (token-info-valid? token-info-data facebook-config)
      (check-fb-email request user-id)
      (do (log/error (str "Facebook token info error: " (get-in token-info-body [:error :message]) " with status code " (:status token-info-response)))
          error-redirect))))

(defn check-access-token [request]
  (let [access-token-response (get-access-token request)
        access-token-body (response->json access-token-response)]
    (if (:error access-token-body)
      (do (log/error (str "Facebook access token error: " (:error access-token-body) " with status code " (:status access-token-response)))
          error-redirect)
      (check-token-info request (:access_token access-token-body)))))

(defn facebook-callback [{:keys [params] :as request}]
  (cond
    (= (:error params) "access_denied") (do (log/error (str "Facebook callback error: access_denied, " (:error_description params)))
                                            (response/redirect utils/host-url))
    (:error params) (do (log/error (str "Facebook callback error: " (:error params) ", " (:error_description params)))
                        error-redirect)
    :default (check-access-token request)))

(def facebook-routes
  ["/" {"facebook-sign-in"  :sign-in
        "facebook-callback" :callback}])

(defn wrap-handler [handler facebook-config]
  (fn [request] (handler (assoc request :facebook-config facebook-config))))

(defn facebook-handlers [facebook-config]
  {:sign-in  (wrap-handler facebook-sign-in facebook-config)
   :callback (wrap-handler facebook-callback facebook-config)})

(defn facebook-workflow [facebook-config]
  (make-handler facebook-routes (facebook-handlers facebook-config)))
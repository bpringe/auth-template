(ns simplefi.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.body-params :refer [body-params]]
            [io.pedestal.http.secure-headers :as sec-headers]
            [io.pedestal.http.csrf :as csrf]
            [clojure.string :as str]
            [ring.util.response :as ring-response]
            [io.pedestal.interceptor.chain :as int-chain]
            [simplefi.config :as config]
            [hiccup.page :as page]
            [hiccup.form :as form]
            [clojure.spec.alpha :as s]
            [buddy.hashers :as hashers]
            [simplefi.db :as db]
            [java-time :as jt]
            [simplefi.email :refer [send-email-verification-email!
                                    send-account-exists-email!
                                    send-password-reset-email!
                                    send-dummy-password-reset-email!]]
            [simplefi.util :as util]
            [simplefi.db-session-store :refer [->DbSessionStore]]))

;;;; Helpers

(defn response
  ([status body]
   (response status body {}))
  ([status body headers]
   {:status status :body body :headers headers}))

(def ok (partial response 200))
(def bad-request (partial response 400))
(def unauthorized (partial response 401))

(defn ok-html
  [html]
  (ok html {"Content-Type" "text/html"}))

(defn get-anti-forgery-token
  [request]
  ;; Pulls from session if token exists there, otherwise pulls from request
  (or (csrf/existing-token request)
      (::csrf/anti-forgery-token request)))

(defn new-session
  ([request]
   (new-session request nil))
  ([request user-id]
   {:user-id user-id ;; If nil, it's an anonymous session
    :user-agent (get-in request [:headers "user-agent"])
    :ip-address (or (get-in request [:headers "x-real-ip"])
                    (:remote-addr request))
    csrf/anti-forgery-token-str (::csrf/anti-forgery-token request)
    :expires-at (jt/plus (jt/local-date-time)
                         (jt/minutes config/session-valid-minutes))}))

;;;; Validation

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(s/def ::email (s/and string? #(re-matches email-regex %) #(<= (count %) 254)))

(defn validate-email
  [email]
  (when-not (s/valid? ::email email)
    "Invalid email"))

(defn validate-password
  [password]
  (cond
    (> (count password) 64) "Password cannot be more than 64 characters"
    (< (count password) 8) "Password must be at least 8 characters"))

;;;; Views

(defn create-page
  [title & content]
  (page/html5
   {:lang "en"}
   [:head
    [:meta {:charset "utf-8"}]
    [:title title]
    [:meta {:name "description" :content "A simple app for personal finance"}]]
   (apply conj [:body] content)))

(defn signup-page
  [{:keys [anti-forgery-token email password password-repeated email-error password-error]}]
  (create-page 
   "Sign up"
   (form/form-to
    [:post "/signup"]
    (form/text-field {:type "hidden"} csrf/anti-forgery-token-str anti-forgery-token)
    [:div
     "Email:" [:br]
     (form/text-field "email" email)
     [:div email-error]]
    [:div
     "Password:" [:br]
     (form/password-field "password" password)]
    [:div
     "Repeat password:" [:br]
     (form/password-field "password-repeated" password-repeated)
     [:div password-error]]
    [:div
     (form/submit-button "Sign up")]
    [:div [:a {:href "/login"} "Have an account? Log in!"]])))

(defn email-verification-sent-page
  [email]
  (create-page
   "Verify email"
   [:div (format "Please check your email, %s, and click the link to verify your email address and begin using your account." email)]))

(defn email-verified-page
  []
  (create-page 
   "Email verified"
   [:div "Your email has been verified! "
    [:a {:href (str config/root-app-url "/login")} "Click here"]
    " to log in."]))

(defn email-verification-error-page
  []
  (create-page 
   "Error verifying email"
   [:div "There was an error verifying your email address. Please "
    [:a {:href (str config/root-app-url "/login")} "click here"]
    " to log in and have a new verification email sent to you."]))

(defn login-page
  [{:keys [anti-forgery-token email error]}]
  (create-page
   "Log in"
   (form/form-to
    [:post "/login"]
    (form/hidden-field csrf/anti-forgery-token-str anti-forgery-token)
    [:div error]
    [:div "Email:" [:br]
     (form/text-field "email" email)]
    [:div "Password:" [:br]
     (form/password-field "password")]
    [:div (form/submit-button "Log in")]
    [:div [:a {:href "/forgotpassword"} "Forgot password?"]]
    [:div [:a {:href "/signup"} "New user? Sign up!"]])))

(defn forgot-password-page
  [{:keys [anti-forgery-token email email-error]}]
  (create-page
   "Forgot password"
   (form/form-to
    [:post "/forgotpassword"]
    (form/hidden-field csrf/anti-forgery-token-str anti-forgery-token)
    [:div "Enter the email you signed up with:" [:br]
     (form/text-field "email" email)
     [:div email-error]]
    [:div (form/submit-button "Email password reset link")])))

(defn password-reset-email-sent-page
  [email]
  (create-page
   "Password reset email sent"
   [:div (format "If an account exists with that email address (%s), an email was sent with a link to reset the password." email)]))

(defn password-reset-page
  [{:keys [anti-forgery-token reset-token error]}]
  (create-page
   "Reset password"
   (form/form-to
    [:post "/resetpassword"]
    (form/hidden-field csrf/anti-forgery-token-str anti-forgery-token)
    (form/hidden-field "reset-token" reset-token)
    [:h3 "Reset your password"]
    [:div "New password:" [:br]
     (form/password-field "password")]
    [:div "Repeat new password:" [:br]
     (form/password-field "password-repeated")]
    [:div error]
    [:div (form/submit-button "Reset password")])))

(defn password-reset-error-page
  []
  (create-page
   "Password reset error"
   [:div "The token for resetting your password has expired or does not exist. Please "
    [:a {:href "/forgotpassword"} "click here"] " and enter your email to have another reset link sent to you."]))

(defn landing-page
  []
  (create-page
   "SimpleFi"
   [:a {:href "/login"} "Log in"] "&nbsp;"
   [:a {:href "/signup"} "Sign up"]
   [:h1 "SimpleFi"]
   [:p "The personal finance app that's so simple, it doesn't even use CSS."]))

(defn app-page
  []
  (create-page
   "SimpleFi"
   [:a {:href "/logout"} "Log out"]
   [:br] [:br]
   [:iframe {:width 560 :height 315
             :src "https://www.youtube.com/embed/dQw4w9WgXcQ?controls=0&autoplay=true"
             :frameborder 0}]
   [:script {:src "/js/main.js" :type "text/javascript"}]))

;;;; Interceptors and handlers

(def anonymous-session
  {:name ::anonymous-session
   :leave 
   (fn [context]
     (let [response (:response context)
           session-key (:session/key response)
           session (:session response)]
       ;; Checking if session is empty here allows handlers to set custom session data (this will not overwrite it)
       (if (and (nil? session-key) (empty? session))
         (assoc-in context [:response :session] (new-session (:request context)))
         context)))})

(defn signup
  [request]
  (ok-html (signup-page {:anti-forgery-token (get-anti-forgery-token request)})))

(defn create-new-email-verification!
  [user-id email]
  (let [token (util/generate-uuid)
        expires-at (jt/plus (jt/local-date-time) 
                            (jt/minutes config/email-verification-token-valid-minutes))]
    (db/insert-email-verification! user-id email token expires-at)
    (send-email-verification-email! email token)))

(defn signup-submit
  [request]
  (let [email (get-in request [:form-params :email])
        password (get-in request [:form-params :password])
        password-repeated (get-in request [:form-params :password-repeated])
        email-error (validate-email email)
        password-error (if (= password password-repeated)
                         (validate-password password)
                         "Passwords did not match")]
    (if (or email-error password-error)
      (ok-html (signup-page {:email email
                             :email-error email-error
                             :password-error password-error
                             :anti-forgery-token (get-anti-forgery-token request)}))
      (let [password-hash (hashers/derive password)]
        (if (db/get-user-by-email email)
          (do
            (send-account-exists-email! email)
            (ok-html (email-verification-sent-page email)))
          (do
            (db/insert-user! email password-hash)
            (let [{user-id :users/id} (db/get-user-by-email email)]
              (create-new-email-verification! user-id email)
              (ok-html (email-verification-sent-page email)))))))))

(defn verify-email
  [request]
  (let [token (-> request :query-params :token)]
    (if-let [verification (db/get-email-verification-by-token token)]
      (let [expires-at (-> verification :email_verifications/expires_at (jt/local-date-time))
            id (-> verification :email_verifications/id)]
        (if (jt/before? (jt/local-date-time) expires-at)
          (do
            (db/set-email-verification-to-verified! id)
            (ok-html (email-verified-page)))
          (ok-html (email-verification-error-page))))
      (ok-html (email-verification-error-page)))))

(defn login
  [request]
  (ok-html (login-page {:anti-forgery-token (get-anti-forgery-token request)})))

(defn login-submit
  [request]
  (let [email (get-in request [:form-params :email])
        password (get-in request [:form-params :password])
        email-error (validate-email email)
        password-error (validate-password password)
        email-or-password-incorrect-response
        (ok-html (login-page {:anti-forgery-token (get-anti-forgery-token request)
                              :email email
                              :error "Email or password is incorrect"}))]
    (if (or email-error password-error)
      email-or-password-incorrect-response
      (let [user (db/get-user-by-email email)
            user-id (:users/id user)]
        (if (and user (hashers/check password (:users/password_hash user)))
          (if-let [email-verification (db/get-email-verification-for-user user-id email)]
            (if (:email_verifications/verified email-verification)
              (-> (ring-response/redirect "/")
                  (assoc :session (with-meta (new-session request user-id) {:recreate true})))
              (let [expires-at (-> email-verification :email_verifications/expires_at (jt/local-date-time))]
                (if (jt/before? (jt/local-date-time) expires-at)
                  (ok-html (email-verification-sent-page email))
                  (do
                    (create-new-email-verification! user-id email)
                    (ok-html (email-verification-sent-page email))))))
            (do
              (create-new-email-verification! user-id email)
              (ok-html (email-verification-sent-page email))))
          email-or-password-incorrect-response)))))

(defn logout
  [_]
  (-> (ring-response/redirect "/")
      (assoc :session nil)))

(defn forgot-password
  [request]
  (ok-html (forgot-password-page {:anti-forgery-token (get-anti-forgery-token request)})))

(defn forgot-password-submit
  [request]
  (let [email (get-in request [:form-params :email])
        email-error (validate-email email)]
    (if email-error
      (ok-html (forgot-password-page
                {:anti-forgery-token (get-anti-forgery-token request)
                 :email email
                 :email-error email-error}))
      (do
        (if-let [user (db/get-user-by-email email)]
          (let [token (util/generate-uuid)]
            (db/insert-password-reset-token!
             {:user-id (:users/id user)
              :reset-token token
              :expires-at (jt/plus (jt/local-date-time)
                                   (jt/minutes config/password-reset-token-valid-minutes))})
            (send-password-reset-email! email token))
          ;; Send an email either way to prevent time-based attacks
          (send-dummy-password-reset-email! email))
        (ok-html (password-reset-email-sent-page email))))))

(defn reset-password
  [request]
  (let [reset-token (-> request :query-params :token)]
    (ok-html (password-reset-page
              {:anti-forgery-token (get-anti-forgery-token request)
               :reset-token reset-token}))))

(defn reset-password-submit
  [request]
  (let [password (get-in request [:form-params :password])
        password-repeated (get-in request [:form-params :password-repeated])
        reset-token (get-in request [:form-params :reset-token])
        error (cond
                (not= password password-repeated) "Passwords did not match"
                :else (validate-password password))]
    (if error
      (ok-html (password-reset-page {:anti-forgery-token (get-anti-forgery-token request)
                                     :error error
                                     :reset-token reset-token}))
      (if-let [{token-id :password_reset_tokens/id
                user-id :password_reset_tokens/user_id}
               (db/get-valid-password-reset-token reset-token)]
        (let [password-hash (hashers/derive password)]
          (db/set-password-reset-token-to-used! token-id)
          (db/invalidate-user-sessions! user-id)
          (db/set-password-hash-for-user! password-hash user-id)
          (-> (ring-response/redirect "/")
              (assoc :session (with-meta (new-session request user-id) {:recreate true}))))
        (ok-html (password-reset-error-page))))))

(defn authenticated?
  [request]
  (boolean (get-in request [:session :identity])))

(def authenticate
  {:name ::authenticate
   :enter
   (fn [context]
     (let [request (:request context)]
       (if (authenticated? request)
         context
         (int-chain/terminate (assoc context :response (ring-response/redirect "/"))))))})

(defn landing-or-app
  [request]
  (if (authenticated? request)
    (ok-html (app-page))
    (ok-html (landing-page))))

(def routes
  #{["/" :get [anonymous-session landing-or-app] :route-name :landing-or-app]
    ["/signup" :get [anonymous-session signup] :route-name :signup]
    ["/signup" :post signup-submit :route-name :signup-submit]
    ["/verifyemail" :get [anonymous-session verify-email] :route-name :verify-email]
    ["/login" :get [anonymous-session login] :route-name :login]
    ["/login" :post [(body-params) login-submit] :route-name :login-submit]
    ["/logout" :get [logout] :route-name :logout]
    ["/forgotpassword" :get [anonymous-session forgot-password] :route-name :forgot-password]
    ["/forgotpassword" :post forgot-password-submit :route-name :forgot-password-submit]
    ["/resetpassword" :get [anonymous-session reset-password] :route-name :reset-password]
    ["/resetpassword" :post reset-password-submit :route-name :reset-password-submit]})

(def service {:env :prod
              ::http/routes routes
              ::http/resource-path "/public"
              ::http/type :jetty
              ::http/port config/service-port
              ::http/host "0.0.0.0"
              ::http/enable-session {:cookie-name config/session-cookie-name
                                     :cookie-attrs {:http-only true
                                                    :secure true
                                                    :same-site :lax}
                                     :store (->DbSessionStore)}
              ::http/enable-csrf {}
              ::http/secure-headers
              {:content-security-policy-settings
               ;; This removes the default 'strict-dynamic' that pedestal adds to the CSP header script-src,
               ;; which requires a nonce or hash in the script tag.
               ;; May want to add this back later for added security?
               (sec-headers/content-security-policy-header
                {:object-src "'none'"
                 :script-src "'unsafe-inline' 'unsafe-eval' https: http:"})}})

(comment
  (def field "hello")
  {field "world"}
  (ring-response/redirect "/")
  (validate-email "hello@gmail.com")
  (-> (repeat 65 "x")
      (clojure.string/join))
  (signup-page {:email-error "Invalid email"
                :password-error "Invalid password"
                :af-token "some-token"})

  (hashers/derive "password" {:alg :bcrypt+sha512
                              :salt "1111111111111111"})
  (hashers/check "password" "bcrypt+sha512$b145824bba02107a0c62de486b6080c0$12$4b8f5a5ad52dd0f560e25baeb15e163b4d24fcc85428d314")
  (ok-html (email-verification-sent-page "hello"))
  (ok-html (email-verified-page))
  (def verification {:verified false})
  (if (:verified verification)
    "verified"
    "not verified")
  (def email-verification (db/get-email-verification-for-user 11 "brandon.ringe@gmail.com"))
  (jt/before? (jt/local-date-time) (-> email-verification :email_verifications/expires_at (jt/local-date-time)))
  (login-page {:anti-forgery-token "some-token"})
  
  (apply conj [:body] [[:a] [:br]]))

(ns auth-template.email
  (:require [postal.core :as postal]
            [auth-template.config :as config]))

(defn send-email!
  [to subject body]
  (postal/send-message
   {:host "smtp.gmail.com"
    :user config/email-user
    :pass config/email-password
    :port 587
    :tls true}
   {:from config/email-user
    :to to
    :subject subject
    :body body}))

(defn send-email-verification-email!
  [to token]
  (send-email!
   [to]
   "auth-template: Verify your email dddress"
   (format "You're receiving this email in regard to your account at %s. Please click the link below or copy and paste it into your browser to verify your email address.\n\n%s/verifyemail?token=%s"
           config/root-app-url
           config/root-app-url
           token)))

(defn send-account-exists-email!
  [to]
  (send-email!
   [to]
   "auth-template: Account already exists"
   (format "A signup request was made for this email address at %s. An account already exists for this email address. Please click the link below to log in. If you did not make this signup request, please ignore this email.\n\n%s/login"
           config/root-app-url config/root-app-url)))

(defn send-password-reset-email!
  [to token]
  (send-email!
   [to]
   "auth-template: Password reset"
   (format "A request was made to reset your password at %s. Please click the link below to reset your password. If you did not make this request, please ignore this email.\n\n%s/resetpassword?token=%s"
           config/root-app-url config/root-app-url token)))

(defn send-dummy-password-reset-email!
  "The purpose of this is mainly to prevent time-based attacks on the password reset form to enumerate non-users."
  [reset-email]
  (send-email!
   [config/alert-email]
   "auth-template: Password reset requested for non-user"
   (format "A password reset was requested for email %s, which does not exist in the database."
          reset-email)))

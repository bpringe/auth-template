(ns simplefi.config
  (:require [config.core :refer [env]]))

(defn- get-config-value
  ([key]
   (get-config-value key nil))
  ([key default-value]
   (if-let [value (get env key)]
     value
     (or default-value 
         (throw (Exception. (str "Config value missing for variable " key)))))))

(defonce service-port (get-config-value :service-port 3000))
(defonce root-app-url (get-config-value :root-app-url))
(defonce session-cookie-name (get-config-value :session-cookie-name))
(defonce email-user (get-config-value :email-user))
(defonce email-password (get-config-value :email-password))
(defonce db-host (get-config-value :db-host))
(defonce db-name (get-config-value :db-name))
(defonce db-user (get-config-value :db-user))
(defonce db-password (get-config-value :db-password))
(defonce alert-email (get-config-value :alert-email))
(defonce session-valid-minutes (get-config-value :session-valid-minutes))
(defonce email-verification-token-valid-minutes (get-config-value :email-verification-token-valid-minutes))
(defonce password-reset-token-valid-minutes (get-config-value :password-reset-token-valid-minutes))

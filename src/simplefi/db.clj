(ns simplefi.db
  (:require [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [java-time :as jt]
            [simplefi.config :as config]
            [clojure.string :as str])
  (:import (com.zaxxer.hikari HikariDataSource)))

(def db-spec {:dbtype "postgresql"
              :dbname config/db-name
              :host config/db-host
              :username config/db-user
              :password config/db-password})

(def ds (connection/->pool HikariDataSource db-spec))

(defn insert-user!
  [email password-hash]
  (jdbc/execute-one! ds ["INSERT INTO users (email, password_hash) VALUES (?, ?)"
                         email password-hash]))

(defn get-user-by-email
  [email]
  (jdbc/execute-one! ds ["SELECT id, email, password_hash FROM users WHERE lower(email) = ?"
                         (str/lower-case email)]))

(defn insert-email-verification!
  [user-id email verification-token expires-at]
  (jdbc/execute-one! ds ["INSERT INTO email_verifications (user_id, email, verification_token, expires_at) VALUES (?, ?, ?, ?)"
                         user-id email verification-token expires-at]))

(defn get-email-verification-by-token
  [token]
  (jdbc/execute-one! ds ["SELECT id, expires_at FROM email_verifications WHERE verification_token = ?" token]))

(defn set-email-verification-to-verified!
  [id]
  (jdbc/execute-one! ds ["UPDATE email_verifications SET verified = true WHERE id = ?" id]))

(defn get-email-verification-for-user
  [user-id email]
  (jdbc/execute-one! ds ["
select expires_at, verified from email_verifications
where user_id = ?
  and lower(email) = ?
order by expires_at desc" user-id (clojure.string/lower-case email)]))

(defn insert-session!
  [{:keys [user-id session-key user-agent ip-address expires-at anti-forgery-token]}]
  (jdbc/execute-one! ds ["
INSERT INTO sessions (user_id, session_key, user_agent, ip_address, expires_at, anti_forgery_token)
VALUES (?, ?, ?, ?::inet, ?, ?)"
                         user-id session-key user-agent ip-address expires-at anti-forgery-token]))

(defn get-session-data
  [key]
  (jdbc/execute-one! ds ["
select s.user_id, s.user_agent, s.ip_address::text, s.anti_forgery_token, u.email
from sessions s left outer join users u on (s.user_id = u.id)
where session_key = ?
  and expires_at > now ()" key]))

(defn expire-session!
  [session-key]
  (jdbc/execute-one! ds ["
update sessions
set expires_at = now ()
where session_key = ?" session-key]))

(defn insert-password-reset-token!
  [{:keys [user-id reset-token expires-at]}]
  (jdbc/execute-one! ds ["
INSERT INTO password_reset_tokens (user_id, reset_token, expires_at)
VALUES (?, ?, ?)" user-id reset-token expires-at]))

(defn get-valid-password-reset-token
  [token]
  (jdbc/execute-one! ds ["
select id, user_id from password_reset_tokens
where reset_token = ?
	and expires_at > now()
	and used_at is null" token]))

(defn set-password-reset-token-to-used!
  [id]
  (jdbc/execute-one! ds ["
update password_reset_tokens
set used_at = now()
where id = ?" id]))

(defn invalidate-user-sessions!
  [user-id]
  (jdbc/execute-one! ds ["
update sessions
set expires_at = now ()
where user_id = ?" user-id]))

(defn set-password-hash-for-user!
  [password-hash user-id]
  (jdbc/execute-one! ds ["
update users
set password_hash = ?
where id = ?" password-hash user-id]))

(comment
  (insert-user! "test1@gmail.com" "password")

  (insert-email-verification! 2 "brandon.ringe@gmail.com" "some-token3" (jt/plus (jt/local-date-time) (jt/days 1)))

  (get-email-verification-by-token "placeholder-token")

  (set-email-verification-to-verified! 7)
  (get-user-by-email "brandon.ringe@gmail.com")
  (get-email-verification-for-user 16 "brandon.ringe@gmail.com")
  (def email-verification (get-email-verification-for-user 11 "brandon.ringe@gmail.com"))
  (jt/before? (jt/local-date-time) (:email_verifications/expires_at email-verification))
  (insert-session! {:user-id 12
                    :session-key "some-key5"
                    :user-agent "Mozilla"
                    :ip-address "127.0.0.1"
                    :expires-at (jt/plus (jt/local-date-time) (jt/days 1))
                    :anti-forgery-token "some-token"})
  (-> (get-session-data "f682849a-f894-4b12-9180-c94f368d1362")
      :sessions/ip
      str)
  (expire-session! "some-key5")
  (insert-password-reset-token! {:user-id 14
                                 :reset-token (simplefi.util/generate-uuid)
                                 :expires-at (jt/plus (jt/local-date-time) (jt/days 1))})
  (set-password-reset-token-to-used! 3))
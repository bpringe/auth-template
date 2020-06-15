(ns simplefi.db-session-store
  (:require [ring.middleware.session.store :refer [SessionStore]]
            [simplefi.db :as db]
            [simplefi.util :as util]
            [java-time :as jt]
            [io.pedestal.http.csrf :refer [anti-forgery-token-str]]))

(deftype DbSessionStore []
  SessionStore
  (read-session
   [_ key]
   (prn "read-session called with key:" key)
   (when-let [{user-agent :sessions/user_agent
               ip-address :ip_address
               user-id :sessions/user_id
               email :users/email
               anti-forgery-token :sessions/anti_forgery_token} 
              (db/get-session-data key)]
     (cond-> {:user-agent user-agent
              :ip-address ip-address
              anti-forgery-token-str anti-forgery-token}
       user-id (assoc :identity {:user-id user-id
                                 :email email}))))
  (write-session
   [_ key {:keys [user-id user-agent ip-address expires-at] :as data}]
   (prn "write-session called with key:" key)
   (let [session-key (or key (util/generate-uuid))]
     ;; Sessions data is immutable, besides deleting/expiring the session.
     (when-not key
       (db/insert-session!
        {:user-id user-id
         :session-key session-key
         :user-agent user-agent
         :ip-address ip-address
         :anti-forgery-token (get data anti-forgery-token-str)
         :expires-at expires-at}))
     session-key))
  (delete-session
   [_ key]
   (prn "delete-session called")
   (db/expire-session! key)
   nil))

(comment
  (.read-session (->DbSessionStore) "f682849a-f894-4b12-9180-c94f368d1362")
  (.write-session (->DbSessionStore)
                  nil
                  {:user-id 12
                   :user-agent "Mozilla"
                   :ip-address "127.0.0.1"
                   :expires-at (jt/plus (jt/local-date-time) (jt/days 1))
                   "__anti-forgery-token" "something"})
  (.delete-session (->DbSessionStore) "4d0306fb-6c25-4334-ba22-03da64fbb50f")
  (get-in {"hello" "world"} ["hello"])
  (.write-session (->DbSessionStore)
                  nil
                  {:user-agent "Mozilla"
                   :ip-address "127.0.0.1"
                   :expires-at (jt/plus (jt/local-date-time) (jt/days 1))}))
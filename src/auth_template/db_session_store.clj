(ns auth-template.db-session-store
  (:require [ring.middleware.session.store :refer [SessionStore]]
            [auth-template.db :as db]
            [auth-template.util :as util]
            [java-time :as jt]
            [io.pedestal.http.csrf :refer [anti-forgery-token-str]]))

(deftype DbSessionStore []
  SessionStore
  (read-session
   [_ key]
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
   (db/expire-session! key)
   nil))

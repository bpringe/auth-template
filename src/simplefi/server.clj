(ns simplefi.server
  (:gen-class)
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [simplefi.service :as service]))

(defonce runnable-service (http/create-server service/service))

(defonce dev-server (atom nil))

(defn start-dev
  [& args]
  (println "\nCreating [DEV] server...")
  (reset! dev-server
          (-> service/service ;; start with production configuration
              (merge {:env :dev
                      ::http/join? false
                      ::http/routes #(route/expand-routes service/routes)
                      ::http/allowed-origins {:creds true :allowed-origins (constantly true)}})
              http/default-interceptors
              http/dev-interceptors
              http/create-server
              http/start)))

(defn stop-dev
  []
  (when @dev-server
    (http/stop @dev-server)
    (reset! dev-server nil)))

(defn restart-dev
  []
  (stop-dev)
  (start-dev))

(defn -main
  [& args]
  (println "\nCreating server...")
  (http/start runnable-service))
 
(comment
  (start-dev)
  (stop-dev)
  (restart-dev)
  
  (require '[config.core :refer [env]])
  
  (env :server-port))

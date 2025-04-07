(ns insilicalabs.ai.http
  (:require [clj-http.client :as http])
  (:import (java.io IOException)))


;
; - advanced options (AI provider independent)
;     - :async? true
;         - ;; respond callback
;            (fn [response] (println "response is:" response))
;            ;; raise callback
;            (fn [exception] (println "exception message is: " (.getMessage exception))))
;         - cancel request
;     - :retry-handler ... IOExceptions are automatically retried, but can use retry-handler to control
;     - :debug true





(def ^:const http-request-config-mods {:throw-exceptions false})

(def ^:const http-get-config-mods {:method :get})

(def ^:const http-post-config-mods {:method :post})

(def ^:const fail-point-http-config :http-config)
(def ^:const fail-point-http-request :http-request)

; todo:
; modes:
;   - synch request/response, single response object
;   - asynch request/response, single response object
;   - stream: asynch, stream of response objects
;
;;
;; success     <boolean>
;; fail-point  <key, e.g. ':http-request'>
;; reason      <key or string?>
;;
;; response    <response map>
;; exception   <exception>
;;
(defn ^:impure request
  [config & more-configs]
  ;; Can test handling responses to HTTP failure codes at https://httpstat.us/
  (let [config-in (apply merge config more-configs)]
    (if (nil? config-in)
      {:success    false
       :fail-point fail-point-http-config
       :reason     "Config can't be nil"}
      (if (not (map? config-in))
        {:success    false
         :fail-point fail-point-http-config
         :reason     "Config must be a map"}
        (if (empty? config-in)
          {:success    false
           :fail-point fail-point-http-config
           :reason     "Config map cannot be empty"}
          (let [config-final (merge http-request-config-mods config-in)]
            (if (not (contains? config-final :method))
              {:success    false
               :fail-point fail-point-http-config
               :reason     "Config must contain the key ':method'"}
              (if (not (#{:get :post} (:method config-final)))
                {:success    false
                 :fail-point fail-point-http-config
                 :reason     "The ':method' value in the config must be either ':get' or ':post'"}
                (if (not (contains? config-final :url))
                  {:success    false
                   :fail-point fail-point-http-config
                   :reason     "Config must contain a URL defined in ':url'"}
                  (if (not (string? (:url config-final)))
                    {:success    false
                     :fail-point fail-point-http-config
                     :reason     "The ':url' value in the config must be a string"}
                    (try
                      (let [response (http/request config-final)]
                        (if (http/success? response)
                          {:success true :response response}
                          {:success    false
                           :fail-point fail-point-http-request
                           :reason     (str "HTTP request failed. " (:reason-phrase response) " (" (:status response) ").")
                           :response   response}))
                      (catch IOException e
                        (let [reason-start (str "HTTP request failed with IOException '" (.getName (class e)) "'.")
                              message (str (.getMessage e))
                              reason (if (or (nil? message) (= message ""))
                                       reason-start
                                       (str reason-start " " message "."))]
                          {:success    false
                           :fail-point fail-point-http-request
                           :reason     reason
                           :exception  e}))
                      (catch Exception e
                        (let [reason-start (str "HTTP request failed with Exception '" (.getName (class e)) "'.")
                              message (str (.getMessage e))
                              reason (if (or (nil? message) (= message ""))
                                       reason-start
                                       (str reason-start " " message "."))]
                          {:success    false
                           :fail-point fail-point-http-request
                           :reason     reason
                           :exception  e})))))))))))))


;; todo docs / simple usage. reference docs in 'request'.
(defn ^:impure get
  [& configs]
  (request (apply merge http-get-config-mods configs)))

;; todo docs / simple usage. reference docs in 'request'.
(defn ^:impure post
  [& configs]
  (request (apply merge http-post-config-mods configs)))

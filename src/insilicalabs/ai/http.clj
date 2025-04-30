(ns insilicalabs.ai.http
  (:require [clj-http.client :as http])
  (:import (java.io IOException)))



(def ^:const http-request-config-mods {:throw-exceptions false})

(def ^:const http-get-config-mods {:method :get})

(def ^:const http-post-config-mods {:method :post})


;; success     <boolean>
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
       :reason     "Config can't be nil."}
      (if (not (map? config-in))
        {:success    false
         :reason     "Config must be a map."}
        (if (empty? config-in)
          {:success    false
           :reason     "Config map cannot be empty."}
          (let [config-final (merge http-request-config-mods config-in)]
            (if (not (contains? config-final :method))
              {:success    false
               :reason     "Config must contain the key ':method'."}
              (if (not (#{:get :post} (:method config-final)))
                {:success    false
                 :reason     "The ':method' value in the config must be either ':get' or ':post'."}
                (if (not (contains? config-final :url))
                  {:success    false
                   :reason     "Config must contain a URL defined in ':url'."}
                  (if (not (string? (:url config-final)))
                    {:success    false
                     :reason     "The ':url' value in the config must be a string."}
                    (try
                      (let [response (dissoc (http/request config-final) :http-client)]
                        (if (http/success? response)
                          {:success true
                           :response response}
                          {:success    false
                           :reason     (str "HTTP request failed. " (:reason-phrase response) " (" (:status response) ").")
                           :response   response}))
                      (catch IOException e
                        (let [reason-start (str "HTTP request failed with IOException '" (.getName (class e)) "'.")
                              message (str (.getMessage e))
                              reason (if (or (nil? message) (= message ""))
                                       reason-start
                                       (str reason-start " " message "."))]
                          {:success    false
                           :reason     reason
                           :exception  e}))
                      (catch Exception e
                        (let [reason-start (str "HTTP request failed with Exception '" (.getName (class e)) "'.")
                              message (str (.getMessage e))
                              reason (if (or (nil? message) (= message ""))
                                       reason-start
                                       (str reason-start " " message "."))]
                          {:success    false
                           :reason     reason
                           :exception  e})))))))))))))


;; todo docs / simple usage. reference docs in 'request'. tests.
(defn ^:impure get
  [& configs]
  (request (apply merge http-get-config-mods configs)))

;; todo docs / simple usage. reference docs in 'request'. tests.
(defn ^:impure post
  [& configs]
  (request (apply merge http-post-config-mods configs)))

(ns insilicalabs.ai.http
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http])
  (:import (java.io IOException)))

; http config for http/request vs. http/post
;
; - varies per request
;     - :body (json/generate-string
;               {<model>
;                <stream>>
;                :messages context})})
;
; - generally the same per session
;     - :headers <api token, project, etc.>
;     - :body (json/generate-string
;               {:model    "gpt-4o"
;                :stream   stream
;                <messages>})}
;
; - default
;     - :url "https://api.openai.com/v1/chat/completions"
;     - :method :post
;     - :content-type :json
;     - :accept :json
;
; - AI provider independent
;     - :socket-timeout 1000      ;; in milliseconds
;     - :connection-timeout 1000  ;; in milliseconds
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


; modes:
;   - synch request/response, single response object
;   - asynch request/response, single response object
;   - stream: asynch, stream of response objects


(def ^:const http-request-config-mods {:throw-exceptions false})

(def ^:const http-get-config-mods {:method :get})

(def ^:const http-post-config-mods {:method :post})

(def ^:const fail-point-http-config :http-config)
(def ^:const fail-point-http-request :http-request)


;; todo: doc
;;   - http errors are not returned as exceptions
(defn ^:impure request
  [config & more-configs]
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
                        (let [reason-start (str "HTTP request failed with exception '" (.getName (class e)) "'.")
                              message (str (.getMessage e))
                              reason (if (or (nil? message) (= message ""))
                                       reason-start
                                       (str reason-start " " message "."))]
                          (println message)
                          {:success    false
                           :fail-point fail-point-http-request
                           :reason     reason
                           :exception  e})))))))))))))
;; java.net.MalformedURLException
;; java.net.UnknownHostException
;; ...
;; https://download.java.net/java/early_access/panama/docs/api/java.base/java/net/package-summary.html

;; if 'response' in err, then have ':status' and ':reason-phrase'

;; test status codes:
;; https://httpstat.us/

;; success     <boolean>
;; fail-point  <key, e.g. ':http-request'>
;; reason      <key or string?>
;;
;; response    <response map>
;; exception   <exception>


;; todo docs / simple usage. reference docs in 'request'.
(defn ^:impure get
  [& configs]
  (request (apply merge http-get-config-mods configs)))

;; todo docs / simple usage. reference docs in 'request'.
(defn ^:impure post
  [& configs]
  (request (apply merge http-post-config-mods configs)))


;(defn- complete-impl [config context]
;  (let [stream (get config :stream false)
;        response
;        (http/request
;          (cond->
;            {:method           :post
;             :url              "https://api.openai.com/v1/chat/completions"
;             ;:debug            true
;             :throw-exceptions false
;             :content-type     :json
;             :headers          {"Authorization" (str "Bearer " (:openai-api-key config))}
;             :body             (json/generate-string
;                                 {:model    "gpt-4o"
;                                  :stream   stream
;                                  :messages context})}
;            stream (assoc :as :reader)))]
;    (if (http/success? response)
;      (cond-> response
;        true :body
;        (not stream) (json/parse-string keyword)
;        (not stream) (get-in [:choices 0 :message :content]))
;      response)))


;(defn stream [config context-or-text consumer-fn]
;  (let [context (create-context context-or-text)
;        reader (complete-impl (assoc config :stream true) context)]
;    (sse-stream/read-sse-stream reader consumer-fn)))

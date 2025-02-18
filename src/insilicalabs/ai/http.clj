(ns insilicalabs.ai.http
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http]))

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
;     - :socket-timeout 1000      ;; in milliseconds
;     - :connection-timeout 1000  ;; in milliseconds
;     - :accept :json
;
; - advanced options
;     - :async? true
;         - ;; respond callback
;            (fn [response] (println "response is:" response))
;            ;; raise callback
;            (fn [exception] (println "exception message is: " (.getMessage exception))))
;         - cancel request
;     - Re-using HttpClient between requests: https://github.com/dakrone/clj-http#re-using-httpclient-between-requests
;     - Persistent Connections: https://github.com/dakrone/clj-http#persistent-connections
;     - :retry-handler ... IOExceptions are automatically retried, but can use retry-handler to control
;     - :debug true
;
; - todo
;     - :throw-exceptions false



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

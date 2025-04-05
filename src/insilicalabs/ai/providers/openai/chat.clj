(ns insilicalabs.ai.providers.openai.chat
  (:require
    [cheshire.core :as json]
    [insilicalabs.ai.http :as http]
    [insilicalabs.ai.providers.openai.sse-stream :as sse-stream]))


(def ^:const completions-url-default "https://api.openai.com/v1/chat/completions")


(defn create-messages
  ([messages-or-user-message]
   (cond
     (nil? messages-or-user-message) []
     (string? messages-or-user-message) [{:role "user" :content messages-or-user-message}]
     :else messages-or-user-message))
  ([system-message user-message]
   [{:role "system" :content system-message}
    {:role "user" :content user-message}])
  ([messages system-message user-message]
   (cond -> messages
         (nil? messages) []
         (some? system-message) (conj {:role "system" :content system-message})
         (some? user-message) (conj {:role "user" :content user-message}))))


(defn- create-context-old [context-or-text]
  (cond
    (nil? context-or-text) []
    (string? context-or-text) [{:role "user" :content context-or-text}]
    :else context-or-text))


(defn get-response-as-string
  ([response])
  ([response n]))


(defn get-response-as-string-vector
  [response])


;; todo: consideration
;; auth-config -> removes :api-key
;; request-config -> removes :stream, :messages
;; only things that changes are (in actual request made later): api-key, messages
(defn create-prepared-request
  ([request-config]
   (create-prepared-request {} {} request-config {}))
  ([request-config response-config]
   (create-prepared-request {} {} request-config response-config))
  ([auth-config http-config request-config response-config]
   (let [auth-config (dissoc auth-config :api-key)
         request-config (cond-> request-config
                                (dissoc :messages)
                                (dissoc :stream))
         stream (get response-config :stream false)
         prepared-request (cond->
                            {:url          completions-url-default
                             :content-type :json
                             :accept       :json}
                            stream (assoc :as :reader)
                            (some? (:socket-timeout http-config)) (assoc :socket-timeout (:socket-timeout http-config))
                            (some? (:connection-timeout http-config)) (assoc :connection-timeout (:connection-timeout http-config)))
         prepared-request {:prepared-request prepared-request
                           :auth-config auth-config
                           :request-config request-config}]  ;; todo: response-config
     prepared-request)))


(defn create-prepared-request-from-full-config
  [config]
  (let [{:keys [auth-config http-config request-config response-config]
         :or   {auth-config {} http-config {} request-config {} response-config {}}}
        config]
    (create-prepared-request auth-config http-config request-config response-config)))


(defn- create-headers
  [auth-config]
  (cond-> {"Authorization" (str "Bearer " (:api-key auth-config))}
          (:api-org auth-config) (assoc "OpenAI-Organization" (:api-org auth-config))
          (:api-proj auth-config) (assoc "OpenAI-Project" (:api-proj auth-config))))


;; :auth-config
;;   :api-key
;;
;; :request-config
;;   :model
;;   :messages
;;
;; :response-config
;;   - rules around async and stream interaction?
;;
(defn- complete-impl
  [config]
  (let [{:keys [auth-config http-config request-config response-config]
         :or   {auth-config {} http-config {} request-config {} response-config {}}}
        config
        request-config (dissoc request-config :stream)
        stream (get response-config :stream false)
        response (http/post
                   (cond->
                     {:url          completions-url-default
                      :headers      (create-headers auth-config)
                      :content-type :json
                      :accept       :json
                      :body         (json/generate-string request-config)}
                     stream (assoc :as :reader)
                     (some? (:socket-timeout http-config)) (assoc :socket-timeout (:socket-timeout http-config))
                     (some? (:connection-timeout http-config)) (assoc :connection-timeout (:connection-timeout http-config))
                     ))]
    ))


(defn complete
  ([config api-key]
   (complete-impl (assoc-in config [:auth-config :api-key] api-key)))
  ([config api-key messages-or-user-message]
   (complete-impl (cond-> config
                          true (assoc-in [:auth-config :api-key] api-key)
                          true (assoc-in [:request-config :messages] (create-messages messages-or-user-message)))))
  ([config api-key messages user-message]
   (complete-impl (cond-> config
                          true (assoc-in [:auth-config :api-key] api-key)
                          true (assoc-in [:request-config :messages] (conj messages {:role "user" :content user-message}))))))


(defn- complete-impl-old [config context]
  (let [stream (get config :stream false)
        response (http/post
                   (cond->
                     {:url     completions-url-default
                      :headers (create-headers config)
                      :body    (json/generate-string
                                 {:model    "gpt-4o"
                                  :stream   stream
                                  :messages context})}
                     stream (assoc :as :reader)))]
    (if (:success response)
      (cond-> response
              true :response
              true :body
              (not stream) (json/parse-string keyword)
              (not stream) (get-in [:choices 0 :message :content]))
      response)))

(defn stream-old [config context-or-text consumer-fn]
  (let [context (create-context-old context-or-text)
        reader (complete-impl-old (assoc config :stream true) context)]
    (sse-stream/read-sse-stream-old reader consumer-fn)))

(defn complete-old
  "Perform a simple chat completion given context-or-text. If context-or-text is a string then it will be treated as
  a single message from the user. Otherwise, it will be used as the data for OpenAI's :messages. For example,
  context-or-text can have a value like [{:role \"user\" :content \"Recommend some ice cream flavors.\"}] or
  [{:role \"user\" :content \"Recommend some ice cream flavors.\"}
   {:role \"assistant\" :content \"Here are some ice cream flavors I recommend ...\"}
   {:role \"user\" :content \"Great! Thank you. Now can you ...\"}]

  config must be a map and contain :api-key with a string of your OpenAI api key.

  The three arg form takes the context and a new user message to add to the context.

  config - a map which must contain :api-key
  context - a vector of messages of the form [{:role \"user\" :content \"message...\"}]
  context-or-text a vector of messages or a string message from user
  user-message - a string with a new user message to add to the context"
  ([config context user-message]
   (complete-old config (conj context {:role "user" :content user-message})))
  ([config context-or-text]
   {:pre [(:api-key config)]}
   (let [context (create-context-old context-or-text)]
     (complete-impl-old config context))))

(defn chat
  "Hold a conversation by adding new user messages to the context and completing. This always returns the full context
  with user's new-message and chat completion."
  [config context new-message]
  (let [context (or context [])
        completion (complete-old config context new-message)]
    (conj context {:role "user" :content new-message} {:role "assistant" :content completion})))
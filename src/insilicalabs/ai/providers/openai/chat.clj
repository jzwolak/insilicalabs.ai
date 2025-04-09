(ns insilicalabs.ai.providers.openai.chat
  (:require
    [cheshire.core :as json]
    [insilicalabs.ai.http :as http]
    [insilicalabs.ai.providers.openai.sse-stream :as sse-stream]))


(def ^:const chat-completions-url-default "https://api.openai.com/v1/chat/completions")


;; auth-config
;;   - removes :api-key
;;
;; request-config
;;   - probably want to give :model
;;   - removes :stream, :messages
;;
;; :response-config
;;   - rules around async and stream interaction?
;;
;; only things that changes are (in actual request made later): api-key, messages
(defn create-prepared-request
  ([request-config]
   (create-prepared-request {} {} request-config {}))
  ([request-config response-config]
   (create-prepared-request {} {} request-config response-config))
  ([auth-config http-config request-config response-config]
   (let [auth-config (dissoc auth-config :api-key)
         stream (get response-config :stream false)
         response-config (assoc response-config :stream stream)
         request-config (cond-> request-config
                                true (dissoc :messages)
                                stream (assoc :stream stream))
         prepared-request (cond->
                            {:url          chat-completions-url-default
                             :content-type :json
                             :accept       :json}
                            stream (assoc :as :reader)
                            (some? (:socket-timeout http-config)) (assoc :socket-timeout (:socket-timeout http-config))
                            (some? (:connection-timeout http-config)) (assoc :connection-timeout (:connection-timeout http-config)))
         prepared-request {:prepared-request prepared-request
                           :auth-config      auth-config
                           :request-config   request-config
                           :response-config  response-config}] ;; todo: finish response-config
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


(defn create-messages
  ([system-message user-message]
   (create-messages [] system-message user-message))
  ([messages system-message user-message]
   (cond-> messages
           (nil? messages) []
           (some? system-message) (conj {:role "system" :content system-message})
           (some? user-message) (conj {:role "user" :content user-message}))))


;; returns 'nil' if not successful
(defn get-response-as-string
  ([response]
   (get-response-as-string response 0))
  ([response n]
   (get-in response [:response :body :choices n :message :content])))


;; returns a vector
;; returns nil if not successful
(defn get-response-as-string-vector
  [response]
  (if-not (:success response)
    nil
    (let [choices (get-in response [:response :body :choices])]
      (doall (mapv #(get-in % [:message :content]) choices)))))


(defn- create-context-old [context-or-text]
  (cond
    (nil? context-or-text) []
    (string? context-or-text) [{:role "user" :content context-or-text}]
    :else context-or-text))



;; see 'complete'
(defn- ^:impure complete-request-impl
  [prepared-request]
  (let [{:keys [auth-config request-config prepared-request]
         :or   {auth-config {} request-config {} prepared-request {}}}
        prepared-request
        response (http/post
                   (-> prepared-request
                       (assoc :headers (create-headers auth-config))
                       (assoc :body (json/generate-string request-config))))]
    (assoc response :stream (get-in prepared-request [:request-config :stream]))))


;; see 'complete'
;; dissoc ':stream'?
(defn- complete-response-impl
  [response]
  (let [response (dissoc response :stream)]
    (if (:success response)
      (let [response (if (:stream response)
                       response
                       (assoc-in response [:response :body] (json/parse-string (get-in response [:response :body]) keyword)))]
        response)
      response)))


;; returns a response map with success=true/false response=<the full response>.  to help parse response, call
;; 'get-response-as-string' or 'get-response-as-string-vector'.
;;
;; if NOT streaming (e.g., ':stream' set to 'true'), the [:response :body] has json keys converted to keywords, else not
;;
;; to automatically update the context, use 'chat'
;;
;; REQUIRED:
;; - :auth-config
;;     :api-key
;; - :request-config
;;     :model
;; :response-config
;;   - rules around async and stream interaction?
(defn ^:impure complete
  ([prepared-request api-key messages-or-user-message]
   (complete-response-impl (complete-request-impl (cond-> prepared-request
                                                          true (assoc-in [:auth-config :api-key] api-key)
                                                          true (assoc-in [:request-config :messages] (create-messages nil messages-or-user-message))))))
  ([prepared-request api-key messages user-message]
   (complete-response-impl (complete-request-impl (cond-> prepared-request
                                                          true (assoc-in [:auth-config :api-key] api-key)
                                                          true (assoc-in [:request-config :messages] (conj messages {:role "user" :content user-message})))))))

;; - same as complete, but adds 'user-message' and the response from the AI assistant to 'messages' and returns the response map with 'messages' key
;;   - and gets the 0th choice for the chat completion
;; - the user message is not saved in 'messages' if the request fails; and of course, there's no assistant message saved
(defn ^:impure chat
  [prepared-request api-key messages user-message]
  (let [messages (or messages [])
        completion (complete prepared-request api-key messages user-message)]
    (if-not (:success completion)
      completion
      (assoc completion :messages (conj messages {:role "user" :content user-message} {:role "assistant" :content (get-response-as-string completion)})))))


;; TODO next
;; - some parts of response map are json keys (not clojure keywords)?  but don't convert [:response :body] if streaming
;; - update example.clj
;; - response handling: asynch, streaming, etc.



(defn- complete-impl-old [config context]
  (let [stream (get config :stream false)
        response (http/post
                   (cond->
                     {:url     chat-completions-url-default
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

(defn chat-old
  "Hold a conversation by adding new user messages to the context and completing. This always returns the full context
  with user's new-message and chat completion."
  [config context new-message]
  (let [context (or context [])
        completion (complete-old config context new-message)]
    (conj context {:role "user" :content new-message} {:role "assistant" :content completion})))
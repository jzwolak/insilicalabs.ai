(ns insilicalabs.ai.providers.openai.chat
  (:require
    [clojure.string :as str]
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
                           :response-config  response-config}]
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


(defn create-messages-from-messages-or-user-message
  [messages-or-user-message]
  (if (string? messages-or-user-message)
    (create-messages nil messages-or-user-message)
    messages-or-user-message))


;; - returns content as a string
;; - if response is not successful, returns nil
;; - if no content, returns empty string.  Allows for cases of no content (like for streaming) to avoid checking if
;;   content was present
;;   - for streaming
;;     - possible that the 'delta' didn't have content, such as first and last chunks or finish_reason=tool_calls,
;;       function_call, stop
;;     - will not have multiple choices, so n = 0 always
(defn get-response-as-string
  ([response]
   (get-response-as-string response 0))
  ([response n]
   (if-not (:success response)
     nil
     (let [content (if (:stream response)
                     (get-in response [:response :data :choices n :delta :content])
                     (get-in response [:response :body :choices n :message :content]))]
       (if (nil? content)
         ""
         content)))))


;; todo: parity with `get-response-as-string`, any non-content should be empty string and not nil
;; - returns a vector of content choices as strings
;;   - streaming will have max 1 choice
;;   - if streaming delta content is not present, then returns empty vector
;; - if response is not successful, returns nil
(defn get-response-as-string-vector
  [response]
  (if-not (:success response)
    nil
    (if (:stream response)
      (let [delta-content (get-in response [:response :data :choices 0 :delta :content])]
        (if (nil? delta-content)
          []
          [delta-content]))
      (let [choices (get-in response [:response :body :choices])]
        (doall (mapv #(get-in % [:message :content]) choices))))))


(defn- create-context-old [context-or-text]
  (cond
    (nil? context-or-text) []
    (string? context-or-text) [{:role "user" :content context-or-text}]
    :else context-or-text))


;; see 'complete'
(defn- ^:impure complete-request
  [prepared-request]
  (let [{:keys [auth-config request-config prepared-request]
         :or   {auth-config {} request-config {} prepared-request {}}} prepared-request]
    (http/post
      (-> prepared-request
          (assoc :headers (create-headers auth-config))
          (assoc :body (json/generate-string request-config))
          (dissoc :auth-config)
          (dissoc :request-config)
          (dissoc :response-config)))))


(defn- change-response-to-unsuccessful
  [response reason]
  (-> response
      (assoc :success false)
      (assoc :reason reason)))


(defn- check-response-errors
  [response]
  (let [finish-reason (get-in response [:response :finish_reason])]
    (if (= finish-reason "length")
      (change-response-to-unsuccessful response "Response stopped due to token limit being reached.")
      (if (= finish-reason "content_filter")
        (change-response-to-unsuccessful response "The response was blocked by the content filter for potentially sensitive or unsafe content.")
        response))))


;; normalizes string 'k' to kebab case and convert to keyword
(defn- normalize-string-to-kebab-keyword [k]
  (-> k
      name
      (str/replace #"[_\s]" "-")
      str/lower-case
      keyword))


(defn- normalize-all-string-properties-to-kebab-keyword [headers]
  (into {}
        (map (fn [[k v]]
               [(normalize-string-to-kebab-keyword k) v])
             headers)))


(defn get-contents [m]
  (mapv (comp :content :message) (get-in m [:response :body :choices])))


;; - for both streaming and non-streaming
;;   - if headers exist, then converted to kebab keyword
;; - if non-streaming
;;   - and body exists, then parses json
;;   - checks for errors indicated in the body json
;;
;; - streaming
;;   - (:stream true) requires a handler-fn
;;   - assumes 0th choice
;;     - The n parameter (which controls how many completions to generate) is ignored in streaming mode. Even if you try
;;       to set n > 1, only n = 1 is honored in streaming.
;;
;; see 'complete'
(defn- complete-response
  [response request]
  (let [response (if (contains? (:response response) :headers)
                   (assoc-in response [:response :headers] (normalize-all-string-properties-to-kebab-keyword (get-in response [:response :headers])))
                   response)
        handler-fn (get-in request [:response-config :handler-fn])]
    (if (:stream response)
      (let [reader (get-in response [:response :body])]
        (sse-stream/read-sse-stream reader (update-in response [:response] dissoc :body) handler-fn))
      (let [response (cond-> response
                             (contains? (:response response) :body) (assoc-in [:response :body] (json/parse-string (get-in response [:response :body]) keyword))
                             true (check-response-errors))]
        (if (some? handler-fn)
          (do
            (handler-fn response)
            (let [caller-response {:success (:success response)
                                   :stream  false}
                  caller-response (if (:success response)
                                    (assoc caller-response :messages (get-contents response))
                                    caller-response)]
              caller-response))
          response)))))


;; - Does one chat completion (does not store and reference previous completions).  To store and reference previous
;; completions, use 'chat'.
;;
;; - Consider using 'create-prepared-request' to create a prepared request.
;;
;; - prepared-request:
;;   - :auth-config
;;     - :api-key is overwritten if set
;;   - :request-config
;;     - :url
;;     - :model
;;     - supports request API from OpenAI, but can't set streaming here, do in :response-config.  Per OpenAI API, 'n'
;;       is ignored for streaming.
;;   - :response-config
;;     - set :handler-fn for response to go to a handler function; one is required for streaming
;;     - set :stream=true for streaming else :stream=false or not set for non-streaming
;;
;; - To stream:
;;   - in :response-config, set handler fn in :handler-fn.  that receives all info, including errs.
;;
;; - Returns on success a map such that:
;;   - :success = true
;;   - :response = original HTTP response that includes keywords such as:
;;     - :status
;;     - :reason-phrase
;;     - :headers -- headers converted from string property names to kebab case keywords
;;     - :protocol-version
;;     - :body -- json parsed with string property names converted to keywords.  NOT explicitly kebab case to preserve OpenAI API.
;;   - :stream = true/false if streaming or not
;;   - :stream-end = true/false if stream has ended or not; only set if streaming
;;   - :chunk-counter = int count of chunk; only set if streaming
;;
;; - To get content, use 'get-response-as-string' or 'get-response-as-string-vector'
;;   - For non-streaming, the first content at key sequence [:response :choices 0 :message :content].  Note that
;;     :choices is vector, could be more than 1 if n > 1 in request.
;;   - For streaming, content at key sequence [:response :choices 0 :delta :content].  There's only 1 choice since streaming
;;     does not respect n in request.
;;
;; - Returns on failure a map such that:
;;   - :success = false
;;   - :reason = string reason for the failure
;;   - :exception = the exception obj; only set if an exception occurred
;;   - :response = original HTTP response, if set, that includes keywords such as:
;;     - :status
;;     - :reason-phrase
;;     - :headers -- headers converted from string property names to kebab case keywords
;;     - :protocol-version
;;     - :body -- json parsed with string property names converted to keywords.  NOT explicitly kebab case to preserve OpenAI API.
;;   - :stream = true/false if streaming or not
;;   - :stream-end = true/false if stream has ended or not; only set if streaming
;;   - :chunk-num = int count of chunk; only set if streaming
;;
;; todo: streaming keys set if err? depends on err?
;;
;; - Does not throw exceptions.  All exceptions are captured and returned as maps with :success = false.
;;
(defn ^:impure complete
  ([prepared-request api-key messages-or-user-message]
   (let [messages (create-messages-from-messages-or-user-message messages-or-user-message)
         prepared-request (-> prepared-request
                              (assoc-in [:auth-config :api-key] api-key)
                              (assoc-in [:request-config :messages] messages))
         stream (get-in prepared-request [:response-config :stream] false)
         response (complete-request prepared-request)]
     (complete-response (assoc response :stream stream) prepared-request)))
  ([prepared-request api-key messages user-message]
   (complete prepared-request api-key (conj messages {:role "user" :content user-message}))))


;; - same as complete, but adds 'user-message' and the response from the AI assistant to ':messages' and returns the response map with ':messages' key
;;   - and gets the 0th choice for the chat completion
;; - the user message is not saved in 'messages' if the request fails; and of course, there's no assistant message saved
;; - successful message with finish_reason=stop for streaming returns data for the chat messages.  all other cases return nil.
(defn ^:impure chat
  [prepared-request api-key messages user-message]
  (let [messages (or messages [])
        completion (complete prepared-request api-key messages user-message)]
    (println "COMPLETION: " completion)
    (if-not (:success completion)
      completion
      (if (:stream completion)
        (let [message (:message completion)
              messages (conj messages {:role "user" :content user-message} {:role "assistant" :content message})]
          (-> completion
              (dissoc :message)
              (assoc :messages messages)))
        (if (contains? (:response-config prepared-request) :handler-fn)
          (assoc completion :messages (conj messages {:role "user" :content user-message} {:role "assistant" :content (get-in completion [:messages 0])}))
          (assoc completion :messages (conj messages {:role "user" :content user-message} {:role "assistant" :content (get-response-as-string completion)})))))))



;; todo: keep for documentation reference
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
   (let [context (create-context-old context-or-text)])))


;; todo: keep for documentation reference
(defn chat-old
  "Hold a conversation by adding new user messages to the context and completing. This always returns the full context
  with user's new-message and chat completion."
  [config context new-message]
  (let [context (or context [])
        completion (complete-old config context new-message)]
    (conj context {:role "user" :content new-message} {:role "assistant" :content completion})))
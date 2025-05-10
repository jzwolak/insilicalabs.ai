(ns insilicalabs.ai.providers.openai.chat
  (:require
    [clojure.string :as str]
    [cheshire.core :as json]
    [insilicalabs.ai.http :as http]
    [insilicalabs.ai.providers.openai.sse-stream :as sse-stream]))


(def ^:const chat-completions-url-default "https://api.openai.com/v1/chat/completions")


(defn create-prepared-request
  "Creates and returns a prepared request, suitable for submitting as part of a request.  A prepared request captures
  those values and pre-processes those aspects of the request that typically don't change between subsequent requests.

  A prepared request is a map of four configurations, represented by maps, consisting of:  authentication, HTTP,
  request, and response configurations.

  The authentication configuration is optional.  It may include the organization and project if the user belongs to
  multiple projects or if the API key is a legacy key; otherwise, the authentication configuration can be omitted or
  left as an empty map.  The authentication configuration specifically does NOT include the API key to avoid exposure of
  the key in memory and, if specified in ':api-key', the API key is removed. The authentication configuration is a map
  that consists of the keys:
    - :api-proj → the project, as a string; optional, but required if ':api-org' is set
    - :api-org  → the organization, as a string; optional, but required if ':api-proj' is set

  The HTTP configuration is optional and specifies HTTP parameters.  The HTTP configuration can be omitted or set to an
  empty map if no HTTP configuration changes are desired.  The HTTP configuration is a map that consists of the keys:
    - :socket-timeout     → sets the time after which, when no data is received between the last received data and
                            current, that a timeout is declared; must be a number; optional
    - :connection-timeout → sets the time after which, when no answer is received from the remote machine, that a
                            timeout is declared; must be a number; optional

  The request configuration is required and must specify at least the model to be used.  The request configuration
  specifies the parameters of API request.  The request configuration may consist of any key-value pair as defined in
  the appropriate API to which the request will be submitted, with the JSON property in the API converted to a Clojure
  keyword.  The 'stream' property to enable streaming cannot be set here and, if so, it is removed; set the 'stream'
  property in the response configuration.  The 'messages' property should not be set; if so, it is removed.  The request
  configuration is a map that consists of the keys:
    - :model → the model to use, as a string; required

  The response configuration is optional.  The response configuration specifies the handling of the response.  If no
  response configuration is given, then the response will be a non-streaming response returned to the caller.
    - :handler-fn → the handler function to receive the response; optional but required if 'stream' is 'true'
    - :stream     → 'true' to enable streaming and absent or 'false' to disable streaming

  Does not validate the inputs or the returned prepared request."
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


(defn create-prepared-request-from-config
  "Builds and returns a prepared request from a configuration.  A configuration is a map of the four configurations--
  authentication, HTTP, request, and response--expressed as maps.  Only the request configuration is required; if not
  needed, then the others may be omitted or set to empty maps.

  The config should be a map with keys:
    - :auth-config     → authentication configuration, optional
    - :http-config     → HTTP configuration, optional
    - :request-config  → request configuration, required
    - :response-config → response configuration, optional

  See 'create-prepared-request' for a description of the keys in the configurations and an explanation of the returned
  prepared request.

  Does not validate the inputs or the returned configuration.
  "
  [config]
  (let [{:keys [auth-config http-config request-config response-config]
         :or   {auth-config {} http-config {} request-config {} response-config {}}}
        config]
    (create-prepared-request auth-config http-config request-config response-config)))


(defn- create-headers
  "Creates and returns a map representing the HTTP headers based on the input authorization configuration
  `auth-config`.

  The API key in key `:api-key` is required.  The organization `:api-org` and project `:api-proj` are only needed if the
  user for API key is a member of multiple organizations or if the API key is a legacy key, otherwise these both should
  be omitted.

  The `auth-config` must be a map with keys:
    - :api-key  → the API key, as a string; required
    - :api-org  → the API organization, as a string; optional
    - :api-proj → the API project, as a string; optional
  "
  [auth-config]
  (cond-> {"Authorization" (str "Bearer " (:api-key auth-config))}
          (:api-org auth-config) (assoc "OpenAI-Organization" (:api-org auth-config))
          (:api-proj auth-config) (assoc "OpenAI-Project" (:api-proj auth-config))))


(defn create-messages
  "Creates and returns a vector of messages, representing the conversation to submit as part of a request.

  The `system-message` string argument, optional, adds a system message to the returned messages.  A system message, if
  specified, is always added before a user message, if specified.  To omit the system message, set it to 'nil'.  A
  system message entry takes the form '{:role \"system\" :content system-message}'.

  The `user-message` string argument, optional, adds a user message to the returned messages.  A user message, if
  specified, is always added after a system message, if specified.  To omit the system message, set it to 'nil'.  A user
  message entry take the form '{:role \"user\" :content user-message}'.

  The `messages` argument, optional, may be a vector of messages, an empty vector, or 'nil'.  If `messages` is not
  specified or is 'nil', then the system and user messages are added to an empty vector.  The returned messages takes
  the form:
    [
      {:role \"system\" :content \"<system message>\"},
      {:role \"user\" :content \"<user message>\"}
    ]"
  ([system-message user-message]
   (create-messages [] system-message user-message))
  ([messages system-message user-message]
   (let [messages (if (nil? messages)
                    []
                    messages)]
     (cond-> messages
             (nil? messages) []
             (some? system-message) (conj {:role "system" :content system-message})
             (some? user-message) (conj {:role "user" :content user-message})))))


(defn- create-messages-from-messages-or-user-message
  "Returns a vector representing the conversation (e.g., messages) to submit as part of a request.  If
  `messages-or-user-message` is a string, then returns a messages vector with the `messages-or-user-message` added as
  a user message per 'create-messages'; else, `messages-or-user-message` is returned as-is."
  [messages-or-user-message]
  (if (nil? messages-or-user-message)
    []
    (if (string? messages-or-user-message)
      (create-messages nil messages-or-user-message)
      messages-or-user-message)))


(defn get-response-as-string
  "Returns a response's content as a string.  Operates on both non-streaming and streaming responses.  If the content
  is 'nil', then an empty string is returned; this allows for cases like streaming to avoid checking if content was
  present.  If the response failed, e.g. ':success' is 'false', then 'nil' is returned.  The `response` must be the
  response map as returned by the 'complete' and 'chat' functions.

  Arity 1: (get-response-as-string response)
    - Returns the first content element (index 0) from the response.

  Arity 2: (get-response-as-string response n)
    - Returns the content at index n from the response."
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


(defn get-response-as-string-vector
  "Returns a vector with all content items as strings.  Operates on both non-streaming and streaming responses.  If no
  entries are found, then an empty vector is returned.  If an entry does not contain content or if that content is 'nil'
  then the content is converted to an empty string; this allows for cases like streaming to avoid checking if content
  was present.  If the response failed, e.g. ':success' is 'false', then 'nil' is returned.  The `response` must be the
  response map as returned by the 'complete' and 'chat' functions."
  [response]
  (if-not (:success response)
    nil
    (if (:stream response)
      (let [delta-content (get-in response [:response :data :choices 0 :delta :content])]
        (if (nil? delta-content)
          [""]
          [delta-content]))
      (let [choices (get-in response [:response :body :choices])]
        (mapv #(or (get-in % [:message :content]) "") choices)))))


(defn- ^:impure complete-request
  "Performs the HTTP request, based on the configuration in `prepared-request` for the OpenAI complete API and returns
  the response as a map.

  A prepared request is a map of four configurations, represented by maps, consisting of:  authentication, HTTP,
  request, and response configurations.  This function requires the authentication and request configurations; the
  others are ignored.

  The authentication configuration is required.  It must include the API key specified with `:api-key`.  It may include
  the organization and project if the user belongs to multiple projects or if the API key is a legacy key; otherwise,
  the authentication configuration can be omitted or left as an empty map.  The authentication configuration is a map
  that consists of the keys:
    - :api-key  → the API key; required
    - :api-proj → the project, as a string; optional, but required if ':api-org is set'
    - :api-org  → the organization, as a string; optional, but required if ':api-proj' is set

  The request configuration is required and must specify at least the model to be used and the messages that are part
  of the requested completion.  The request configuration specifies the parameters of API request.  The request
  configuration may consist of any key-value pair as defined in the appropriate API to which the request will be
  submitted, with the JSON property in the API converted to a Clojure keyword.  The 'stream' property to enable
  streaming cannot be set here and, if so, it is removed; set the 'stream' property in the response configuration.  The
  'messages' property should not be set; if so, it is removed.  The request configuration is a map that consists of the
  keys:
    - :model    → the model to use, as a string; required
    - :messages → a vector of one or more messages describing the conversation; required

  On success, the returned map contains key ':success' set to 'true' and ':response' set the HTTP response.

  The HTTP response in ':response' takes the form (selected fields shown):
    :cached <nil for not cached>
    :protocol-version <protocol version>
    :cookies <cookies>
    :reason-phrase <string reason>
    :headers <string headers>
    :status <string status code>
    :body <stringified JSON>

  On failure, the returned map contains key ':success' set to 'false', ':error-code' set to a keyword error code, and
  ':reason' set to a string reason for the error.  If a response was returned, then key ':response' is set to the HTTP
  response; the contents of the response match that in the 'success' example above.  If an exception occurred, then the
  key ':exception' holds the exception object.

  In the case of a failure, the error code in the key ':error-code' provides a programmatic way to determine the cause
  of the failure.  Error codes consist of:
    - :http-config-nil                 → The HTTP configuration (and thus the entire configuration) was `nil`
    - :http-config-not-map             → The HTTP configuration (and thus the entire configuration) was not a map
    - :http-config-empty               → The HTTP configuration (and thus the entire configuration) was an empty map
    - :http-config-unknown-key         → The HTTP configuration contained an unknown key
    - :http-config-method-missing      → The HTTP configuration did not specify the `:method` key to define the HTTP
                                         method, e.g. `GET` or `POST`
    - :http-config-method-invalid      → The HTTP configuration `:method` key was not one of the valid values, either
                                         `:get` or `:post`
    - :http-config-url-missing         → The HTTP configuration did not specify the `:url` key to define the URL to
                                         which to connect
    - :http-config-url-not-string      → The HTTP configuration `:url` key was not a string
    - :http-request-failed             → The HTTP request failed.  See the `:response` key for reason phrase
                                         `:reason-phrase` and status code `:status` in the returned map.  The failure
                                         was not due to an exception.
    - :request-config-missing-api-key  → The request configuration does not contain the key ':api-key' in map
                                         ':auth-config'.
    - :request-config-api-proj-org     → The request configuration contains one of key ':api-proj' or key ':api-org' in
                                         map ':auth-config' but not the other.
    - :request-config-model-missing    → The request configuration does not contain key ':model in map
                                         ':request-config'.
    - :request-config-messages-missing → The request configuration does not contain key ':messages' in map
                                         ':request-config'.
    - :http-request-failed-ioexception → The HTTP request failed due to an `IOException`.  See `:exception` for the
                                         exception in the returned map.
    - :http-request-failed-exception   → The HTTP request failed due an `Exception`.  See `:exception` for the exception
                                         in the returned map.

  This function does not throw exceptions.  All exceptions are handled by returning a map with key ':success' set to
  'false', as above."
  [prepared-request]
  (let [{:keys [auth-config request-config prepared-request]
         :or   {auth-config {} request-config {} prepared-request {}}} prepared-request]
    (if-not (contains? auth-config :api-key)
      {:success    false
       :error-code :request-config-missing-api-key
       :reason     "Request config does not contain key ':api-key' in map ':auth-config'."}
      (if (or
            (and
              (contains? auth-config :api-proj)
              (not (contains? auth-config :api-org)))
            (and
              (contains? auth-config :api-org)
              (not (contains? auth-config :api-proj))))
        {:success    false
         :error-code :request-config-api-proj-org
         :reason     "Request config contains one of ':api-key' or ':api-org' in map ':auth-config' but not the other."}
        (if-not (contains? request-config :model)
          {:success    false
           :error-code :request-config-model-missing
           :reason     "Request config does not contain key ':model' in map ':request-config'."}
          (if-not (contains? request-config :messages)
            {:success    false
             :error-code :request-config-messages-missing
             :reason     "Request config does not contain key ':messages' in map ':request-config'."}
            (http/post
              (-> prepared-request
                  (assoc :headers (create-headers auth-config))
                  (assoc :body (json/generate-string request-config))
                  (dissoc :auth-config)
                  (dissoc :request-config)
                  (dissoc :response-config)))))))))


(defn- change-response-to-unsuccessful
  "Change the response `response` to unsuccessful by setting ':success' to 'false', ':error-code' to `error-code`, and
  ':reason' to `reason`."
  [response error-code reason]
  (-> response
      (assoc :success false)
      (assoc :error-code error-code)
      (assoc :reason reason)))


(defn- check-response-errors
  "Checks for possible response errors in `response` and updates the response accordingly.

  Checks for errors at [:response :finish_reason] for conditions of 'length' or 'content_filter' which indicates an
  error occurred.  If so, changes the response to unsuccessful by setting ':success' to 'false', ':error-code to the
  error code as below, and ':reason' to a reason for the failure.

  The error codes are assigned per finish_reason as follows:
    - length         → :request-failed-limit
    - content_filter → :request-failed-content-filter"
  [response]
  (let [finish-reason (get-in response [:response :finish_reason])]
    (if (= finish-reason "length")
      (change-response-to-unsuccessful response :request-failed-limit "Response stopped due to token limit being reached.")
      (if (= finish-reason "content_filter")
        (change-response-to-unsuccessful response :request-failed-content-filter "The response was blocked by the content filter for potentially sensitive or unsafe content.")
        response))))


(defn- normalize-string-to-kebab-keyword [k]
  "Converts `k` to a kebab keyword.  The input `k` may be a string or keyword."
  (-> k
      name
      (str/replace #"[_\s]" "-")
      str/lower-case
      keyword))


(defn- normalize-all-string-properties-to-kebab-keyword [headers]
  "Normalizes all keys in `headers` to kebab keywords.  The keys may be strings or keywords."
  (into {}
        (map (fn [[k v]]
               [(normalize-string-to-kebab-keyword k) v])
             headers)))


(defn- get-contents [response]
  "Returns a vector of messages extracted from `response`.  The `response` must be a map as returned by 'complete' and
  'chat'.  Operates only on non-streaming responses."
  (mapv (comp :content :message) (get-in response [:response :body :choices])))


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
;; todo: docs
;; todo: tests
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
;;   - for streaming
;;     - possible that the 'delta' didn't have content, such as first and last chunks or finish_reason=tool_calls,
;;       function_call, stop
;;     - will not have multiple choices, so n = 0 always
;; todo: docs
;; todo: tests
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
;; todo: docs
;; todo: tests
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



;; todo: keep for keeping the function below
(defn- create-context-old [context-or-text]
  (cond
    (nil? context-or-text) []
    (string? context-or-text) [{:role "user" :content context-or-text}]
    :else context-or-text))

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
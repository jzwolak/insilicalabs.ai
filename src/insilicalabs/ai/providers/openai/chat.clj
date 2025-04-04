(ns insilicalabs.ai.providers.openai.chat
  (:require
    [cheshire.core :as json]
    [insilicalabs.ai.providers.openai.http :as http]
    [insilicalabs.ai.providers.openai.sse-stream :as sse-stream]))


(def ^:const default-completions-url "https://api.openai.com/v1/chat/completions")


(defn create-context
  ([system-msg user-msg]
   [{:role "system" :content system-msg}
    {:role "user" :content user-msg}])
  ([context system-msg user-msg]
   (cond -> context
         (nil? context) []
         (some? system-msg) (conj {:role "system" :content system-msg})
         (some? user-msg) (conj {:role "user" :content user-msg}))))


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


(defn- create-headers
  [config]
  (cond-> {"Authorization" (str "Bearer " (:api-key config))}
          (:api-org config) (assoc "OpenAI-Organization" (:api-org config))
          (:api-proj config) (assoc "OpenAI-Project" (:api-proj config))))


(defn- complete-impl-old [config context]
  (let [stream (get config :stream false)
        response (http/post
                   (cond->
                     {:url     default-completions-url
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
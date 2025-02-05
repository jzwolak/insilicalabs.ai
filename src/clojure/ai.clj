(ns clojure.ai
    (:require
      [clj-http.client :as http]
      [clojure.pprint :as pprint]
      [cheshire.core :as json]
      [clojure.string :as str]))

(def openai-api-key (str/trim (slurp "/Users/jzwolak/files/secure/integrative-bioinformatics/processdb-open-ai-api-key")))


(defn complete
  "Perform a simple chat completion given context-or-text. If context-or-text is a string then it will be treated as
  a single message from the user. Otherwise, it will be used as the data for OpenAI's :messages. For example,
  context-or-text can have a value like [{:role \"user\" :content \"Recommend some ice cream flavors.\"}] or
  [{:role \"user\" :content \"Recommend some ice cream flavors.\"}
   {:role \"assistant\" :content \"Here are some ice cream flavors I recommend ...\"}
   {:role \"user\" :content \"Great! Thank you. Now can you ...\"}]

  config must be a map and contain :openai-api-key with a string of your OpenAI api key."
  ([config context-or-text]
   {:pre [(:openai-api-key config)]}
   (let [context (if (string? context-or-text)
                   [{:role "user" :content context-or-text}]
                   context-or-text)
         response
         (http/request
           {:method           :post
            :url              "https://api.openai.com/v1/chat/completions"
            ;:debug            true
            :throw-exceptions false
            :content-type     :json
            :headers          {"Authorization" (str "Bearer " (:openai-api-key config))}
            :body             (json/generate-string
                                {:model    "gpt-4o"
                                 :messages context})})]
     (if (http/success? response)
       (->
         response
         :body
         (json/parse-string keyword)
         (get-in [:choices 0 :message :content]))
       response))))
# insilicalabs.ai

Reduce friction when getting up and running with the OpenAI API.

## Quick Start

Create your OpenAI developer account and an API key. Store the api key in a file some place secure.

Then do the following:

```
> clj
=> (require '[clojure.ai :as ai])
=> (def openai-api-key (clojure.string/trim (slurp "openai-api-key.txt"))) ; read api key from file
=> (ai/complete {:openai-api-key openai-api-key} "Recommend some places to travel. Please be terse.")
```

## Chatting

Using Clojure's `*1` variable in the REPL you can hold a chat as follows.

```
=> (def config {:openai-api-key (str/trim (slurp "openai-api-key.txt"))})
=> (ai/chat config [] "Recommend some places to travel. Be terse.")
=>
[{:role "user", :content "Recommend some places to travel. Be terse."}
 {:role "assistant",
  :content "Kyoto, Japan  
            Santorini, Greece  
            Reykjavik, Iceland  
            Marrakech, Morocco  
            Queenstown, New Zealand  
            Cape Town, South Africa  
            Lisbon, Portugal  
            Banff, Canada  
            Seville, Spain  
            Hoi An, Vietnam"}]
=> (ai/chat config *1 "Tell me more about Seville. Be terse.")
=>
[{:role "user", :content "Recommend some places to travel. Be terse."}
 {:role "assistant",
  :content "Kyoto, Japan  
            Santorini, Greece  
            Reykjavik, Iceland  
            Marrakech, Morocco  
            Queenstown, New Zealand  
            Cape Town, South Africa  
            Lisbon, Portugal  
            Banff, Canada  
            Seville, Spain  
            Hoi An, Vietnam"}
 {:role "user", :content "Tell me more about Seville. Be terse."}
 {:role "assistant",
  :content "Seville, in southern Spain, is renowned for its rich Moorish history, flamenco dancing, and stunning architecture. Highlights include the Alcázar Palace, Seville Cathedral, and the Giralda tower. The city offers vibrant tapas bars and the lively Feria de Abril festival. Its historic streets and orange trees create a charming atmosphere."}]
```

And so on.

## Streaming

OpenAI has a streaming API to receive partial completions of a chat request in a stream until the whole completion is
sent. Use this with `stream` as follows. `stream` takes a consumer, which is a single arg fn that will
do something with the partial chat completion.

```
=> (ai/stream config "Recommend some places to travel." #(print %))
```

## Recommendations

When integrating ChatGPT into your app, we recommend wrapping the fns you plan to use with partial fns that include
the config. For example, if your API key is stored in the environment variable "OPENAI_API_KEY" then you might do the
following:

```
(ns example.mycompany
  (:require [insilicalabs.ai :as ai]))

(defn get-config []
  {:openai-api-key (System/getenv "OPENAI_API_KEY")})

(defn chat [context new-message] (ai/chat (get-config) context new-message))
```

Reconstructing the config on each call should be a small cost compared to calling the API and has the benefit of
not storing the API key in memory. If performance is of the utmost concern or retrieving the API key is much more
expensive, then a delay may be useful as follows.

```
(defonce config (delay {:openai-api-key (System/getenv "OPENAI_API_KEY}))

(defn chat [context new-message] (ai/chat @config context new-message))
```

## Error Codes

| Error Code                       | Description                                                                                                                                                                   |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| :http-config-nil                 | The HTTP configuration (and thus the entire configuration) was `nil`                                                                                                          |
| :http-config-not-map             | The HTTP configuration (and thus the entire configuration) was not a map                                                                                                      |
| :http-config-empty               | The HTTP configuration (and thus the entire configuration) was an empty map                                                                                                   |
| :http-config-unknown-key         | The HTTP configuration contained an unknown key                                                                                                                               |
| :http-config-method-missing      | The HTTP configuration did not specify the `:method` key to define the HTTP method, e.g. `GET` or `POST`                                                                      |
| :http-config-method-invalid      | The HTTP configuration `:method` key was not one of the valid values, either `:get` or `:post`                                                                                |
| :http-config-url-missing         | The HTTP configuration did not specify the `:url` key to define the URL to which to connect                                                                                   |
| :http-config-url-not-string      | The HTTP configuration `:url` key was not a string                                                                                                                            |
| :http-request-failed             | The HTTP request failed.  See the `:response` key for reason phrase `:reason-phrase` and status code `:status` in the returned map.  The failure was not due to an exception. |
| :http-request-failed-ioexception | The HTTP request failed due to an `IOException`.  See `:exception` for the exception in the returned map.                                                                     |
| :http-request-failed-exception   | The HTTP request failed due an `Exception`.  See `:exception` for the exception in the returned map.                                                                          |
| :request-config-missing-api-key  | The request configuration does not contain the key ':api-key' in map ':auth-config'.                                                                                          |
| :request-config-api-proj-org     | The request configuration contains one of key ':api-proj' or key ':api-org' in map ':auth-config' but not the other.                                                          |
| :request-config-model-missing    | The request configuration does not contain key ':model in map ':request-config'.                                                                                              |
| :request-config-messages-missing | The request configuration does not contain key ':messages' in map ':request-config'.                                                                                          |
| :request-failed-limit            | The response stopped due to the token limit being reached                                                                                                                     |
| :request-failed-content-filter   | The response was blocked by the content filter for potentially sensitive or unsafe content                                                                                    |
| :stream-event-unknown            | During a streaming response, an unrecognized stream event was received                                                                                                        |
| :stream-event-error              | During a streaming response, a stream error event was received                                                                                                                |
| :stream-read-failed              | During a streaming response, a stream read exception occurred                                                                                                                 |





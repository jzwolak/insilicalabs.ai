# insilicalabs.ai
[![Powered by InSilica Labs](https://img.shields.io/badge/Powered_by-InSilica_Labs-blue?link=https%3A%2F%2Finsilicalabs.com%2Findex.php)](https://insilicalabs.com/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/license/mit)
<p></p>

*Reduce friction and speed up LLM API integration— pass a simple map, get structured results.*

The *InSilica Labs AI* library helps accelerate integration with AI providers' APIs for Large Language Models (LLMs).
The AI library initially supports OpenAI's Chat Completions API, and others will follow.

## Quick Start

### OpenAI

Create your OpenAI developer account and an API key.  Securely store the API key.  The examples presented here assume
the API key is stored in a file.

#### Chat Completions

##### Complete

Created a prepared request.  A prepared request captures and pre-processes those aspects of the request that typically
don't change between subsequent requests.  The `complete` and `chat` functions require a prepared request that, at a
minimum, defines the model to use.

```clojure
(ns example.mycompany
  (:require [insilicalabs.ai.providers.openai.chat :as chat]))
    
(def prepared-request (chat/create-prepared-request {:model "gpt-4o"}))
```

Create initial messages for the completion, if any.  Often an initial 'system' type  message may be desired to prompt 
the model that it is "a helpful assistant".

```clojure
(def messages (chat/create-message "You are a helpful assistant." nil))
```

Obtain the user message to send to the chat completion, `user-message`.


Make the chat completion request.

```clojure
(def response (chat/complete api-key messages user-message))
```

Display the content of the first response item if successful else display an error message.

```clojure
(if (:success response)
  (println (chat/get-response-as-string response))
  (println "ERROR: " response))
```

##### Chat

The `chat` function follows the same process as `complete` above, but the `chat` function also returns updated messages,
on success, that include the user message and the model's response.

Call `chat` instead of `complete`.

```clojure
(def response (chat/chat api-key messages user-message))
```

Display the content of the first response item and the updated messages if successful else display an error message.
When making subsequent calls to `chat`, use the updated messages in `(:messages response)`.

```clojure
(if (:success response)
  (do
    (println (chat/get-response-as-string response))
    (println "UPDATED MESSAGES: " (:messages response)))
  (println "ERROR: " response))
```

##### Streaming

Streamed responses can be provided by setting `:stream` to `true` and by providing a handler function, both in the
prepared request as follows:

```clojure
(defn response-handler-fn [response]
  (if (:success response)
    (print (chat/get-response-as-string response))  ;; note the 'print' vs 'println' statement
    (do
      (println "\n\n")
      (println "ERROR:" response))))

(def prepared-request (chat/create-prepared-request {:model "gpt-4o"} {:stream     true
                                                                       :handler-fn response-handler-fn}))

(def response (chat/chat api-key messages user-message))
```


## Examples

See the `examples` directory for complete examples of using the library.


## Recommendations

To help reduce the API key's exposure in memory, we recommend wrapping the functions you plan to use with partial 
functions that include the config.  For example, if your API key is stored in the environment variable "OPENAI_API_KEY" 
then you might do the following:

```clojure
(ns example.mycompany
  (:require [insilicalabs.ai.providers.openai.chat :as chat]))

(defn get-api-key 
  []
  (System/getenv "OPENAI_API_KEY"))

(def response (chat/chat (get-api-key) messages user-message))
```

If performance is of the utmost concern or retrieving the API key is much more expensive, then a delay may be useful as 
follows.

```clojure
(defonce api-key (delay (System/getenv "OPENAI_API_KEY")))

(defn response 
  [messages user-message] 
  (response/chat @api-key messages user-message))
```


## Building the Project

Run the tests from the command line with: `clj -X:test`.


## License

The *InSilica Labs AI* library is released under the MIT license.





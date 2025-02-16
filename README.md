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
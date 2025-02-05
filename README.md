# clojure.ai

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

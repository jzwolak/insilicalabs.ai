(ns insilicalabs.ai.providers.openai.config)


(defn create-auth-config
  "Creates an authentication configuration without the API key.  Use this function if the API key owner belongs to
  multiple organizations or the API key is a legacy API user key; if at least one of these conditions is not true, then
  this function is likely not needed.

  The `api-proj` sets the `OpenAI-Organization` property, and the `api-org` sets the `OpenAI-Project`.  Both arguments
  must be strings.

  The API key is specifically excluded from this configuration to help promote minimizing the exposure of the key in
  memory."
  [api-proj api-org]
  {:api-proj api-proj
   :api-org  api-org})


(defn create-request-config
  "Creates a request configuration, setting the model to `model`.  Argument `model` must be a string."
  [model]
  {:model model})


(defn create-response-config
  "Creates a response configuration, setting the handler function to `handler-fn` and, if provided, sets the stream
   to `stream`.  Argument `handler-fn` must refer to a function, and `stream` must be 'true' or 'false'."
  ([handler-fn]
   {:handler-fn handler-fn})
  ([handler-fn stream]
   {:handler-fn handler-fn
    :stream stream}))

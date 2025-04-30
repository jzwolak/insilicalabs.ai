(ns insilicalabs.ai.config)


(defn create-auth-config
  "Creates an authentication configuration without the API key.  Use this function if the API key owner belongs to
  multiple organizations or the API key is a legacy API user key; if at least one of these conditions is not true, then
  this function is likely not needed.

  The `api-proj` sets the `OpenAI-Organization` property, and the `api-org` sets the `OpenAI-Project`.

  The API key is specifically excluded from this configuration to help promote minimizing the exposure of the key in
  memory.

  As these settings are likely to remain unchanged during the life the application, then consider creating this
  configuration once and preserving its value (vs. re-creating it with each request), perhaps as a constant."
  [api-proj api-org]
  {:api-proj api-proj
   :api-org  api-org})


;; todo: need to filter out some settings like stream?
(defn create-http-config
  []
  "
  todo

  As these settings are likely to remain unchanged during the life the application, then consider creating this
  configuration once and preserving its value (vs. re-creating it with each request), perhaps as a constant."
  {}
  )

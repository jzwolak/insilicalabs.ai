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


;; todo: filter out stream?  it's handled in the response-config
(defn create-request-config
  "Creates a request configuration, setting the `model` property to `model`.  Specific API endpoints may offer
  additional configuration options.

  As these settings are likely to remain unchanged during the life the application, then consider creating this
  configuration once and preserving its value (vs. re-creating it with each request), perhaps as a constant."
  [model]
  {:model model})


;; todo
;; - stream
;; - handler-fn
(defn create-response-config
  []
  "
  todo

  Creates a response handling configuration.  Specific API endpoints may offer
  additional configuration options.

  As these settings are likely to remain unchanged during the life the application, then consider creating this
  configuration once and preserving its value (vs. re-creating it with each request), perhaps as a constant."
  {}
  )

;; todo: filter out settings that the other configs are also filtering out?
(defn create-config
  "Creates a configuration, suitable for submitting an API request.  Configurations may include authentication
  configurations in `auth-config`, HTTP configurations in `http-config`, request configurations in `request-config`, and
  response configurations in `response-config`.

  The returned configuration will be a map of the form:
    {:auth-config     <auth config>
     :http-config     <HTTP config>
     :request-config  <request config>
     :response-config <response config>}

  Map keys with values where the arguments are 'nil' or 'false' are omitted from the returned map.  If the key `api-key`
  was set in the `auth-config`, then that key is removed from the returned map; this is to help promote minimizing the
  exposure of the key in memory.

  As these settings are likely to remain unchanged during the life the application, then consider creating this
  configuration once and preserving its value (vs. re-creating it with each request), perhaps as a constant."
  ([request-config]
   (create-config nil nil request-config nil))
  ([request-config response-config]
   (create-config nil nil request-config response-config))
  ([auth-config http-config request-config response-config]
   (cond-> {}
           (some? auth-config)     (assoc :auth-config auth-config)
           true                    (true (dissoc auth-config :api-key))
           (some? http-config)     (assoc :http-config http-config)
           (some? request-config)  (assoc :request-config request-config)
           (some? response-config) (assoc :response-config response-config))))

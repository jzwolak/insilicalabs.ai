(ns insilicalabs.ai.providers.openai.auth)


(defn create-auth-config
  "Creates an authentication configuration.

  Use the form '[api-key]' if th key owner does not belong to multiple organizations, or if the key is not a legacy API
  user key. Otherwise, use the form '[api-key api-proj api-org]'.  If the latter form is needed, then it may be useful
  to preserve a value (perhaps as a constant) for the API project and organization but without the API key to minimize
  its exposure in memory.  In that case, create a config without the API key but the API project and organization with
  the form '[nil api-proj api-org'], thus setting `api-key` to nil.  When preparing to send a new request, then add the
  API key with the form '[api-key auth-config]'.

 - (create-auth-config api-key)                  → Accepts the string API key `api-key` and returns it as an auth config
                                                   map.
 - (create-auth-config api-key api-proj api-org) → Accepts a string for the API key `api-key` and adds it to the auth
                                                   config map 'auth-config' then returns the updated auth config map.
 - (create-auth-config api-key api-proj api-org) → Accepts strings for  API key `api-key`, project `api-proj`, and
                                                   organization `api-org` and returns them as an auth config map."
  ([api-key]
   {:api-key api-key})
  ([api-key auth-config]
   (assoc auth-config :api-key api-key))
  ([api-key api-proj api-org]
   {:api-key  api-key
    :api-proj api-proj
    :api-org  api-org}))

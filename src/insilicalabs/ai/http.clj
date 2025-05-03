(ns insilicalabs.ai.http
  (:require [clj-http.client :as http])
  (:import (java.io IOException)))


(def ^:const http-request-config-mods {:throw-exceptions false})
(def ^:const http-get-config-mods {:method :get})
(def ^:const http-post-config-mods {:method :post})


(defn- build-check-config
  "Builds and checks an HTTP configuration, based on the HTTP configuration `config`.  On success returns a map with
  ':success' set to 'true' and the complete and validated HTTP configuration in ':config'.  On failure, returns a map
  with ':success' set to 'false', ':error-code' set to a keyword error code, and ':reason' set to the string reason for
  the failure.

  Adds to the `config` the contents of 'http-request-config-mods'.

  A valid `config`:
    - must be a non-empty map
    - must contain the key ':method' set to either ':get' for a GET request or ':post' for a POST request
    - must contain the key ':url' set to a string"
  [config]
  (if (nil? config)
    {:success    false
     :error-code :http-config-nil
     :reason     "Config can't be nil."}
    (if (not (map? config))
      {:success    false
       :error-code :http-config-not-map
       :reason     "Config must be a map."}
      (if (empty? config)
        {:success    false
         :error-code :http-config-empty
         :reason     "Config map cannot be empty."}
        (let [config-final (merge http-request-config-mods config)]
          (if (not (contains? config-final :method))
            {:success    false
             :error-code :http-config-method-missing
             :reason     "Config must contain the key ':method'."}
            (if (not (#{:get :post} (:method config-final)))
              {:success    false
               :error-code :http-config-method-invalid
               :reason     "The ':method' value in the config must be either ':get' or ':post'."}
              (if (not (contains? config-final :url))
                {:success    false
                 :error-code :http-config-url-missing
                 :reason     "Config must contain a URL defined in ':url'."}
                (if (not (string? (:url config-final)))
                  {:success    false
                   :error-code :http-config-url-not-string
                   :reason     "The ':url' value in the config must be a string."}
                  {:success true
                   :config  config-final})))))))))


(defn ^:impure request
  "Performs the HTTP request as specified by the configuration `config` with the merger of zero or more additional
  configurations in `more-configs` and returns the result as a map.

  A valid configuration:
    - must be a non-empty map
    - must contain the key ':method' set to either ':get' for a GET request or ':post' for a POST request
    - must contain the key ':url' set to a string

  On success, the returned map contains key ':success' set to 'true' and ':response' set the HTTP response.

  The HTTP response in ':response' takes the form (selected fields shown):
    :cached <nil for not cached>
    :protocol-version <protocol version>
    :cookies <cookies>
    :reason-phrase <string reason>
    :headers <string headers>
    :status <string status code>
    :body <stringified JSON>

  On failure, the returned map contains key ':success' set to 'false', ':error-code' set to a keyword error code, and
  ':reason' set to a string reason for the error.  If a response was returned, then key ':response' is set to the HTTP
  response; the contents of the response match that in the 'success' example above.  If an exception occurred, then the
  key ':exception' holds the exception object.

  This function does not through exceptions.  All exceptions are handled by returning a map with key ':success' set to
  'false', as above."
  [config & more-configs]
  ;; Can test handling responses to HTTP failure codes at https://httpstat.us/
  (let [config-in (apply merge config more-configs)
        config-updated (build-check-config config-in)]
    (if-not (:success config-updated)
      config-updated
      (try
        (let [response (dissoc (http/request config-updated) :http-client)]
          (if (http/success? response)
            {:success  true
             :response response}
            {:success    false
             :error-code :http-request-failed
             :reason     (str "HTTP request failed. " (:reason-phrase response) " (" (:status response) ").")
             :response   response}))
        (catch IOException e
          (let [reason-start (str "HTTP request failed with IOException '" (.getName (class e)) "'.")
                message (str (.getMessage e))
                reason (if (or (nil? message) (= message ""))
                         reason-start
                         (str reason-start " " message "."))]
            {:success    false
             :error-code :http-request-failed-ioexception
             :reason     reason
             :exception  e}))
        (catch Exception e
          (let [reason-start (str "HTTP request failed with Exception '" (.getName (class e)) "'.")
                message (str (.getMessage e))
                reason (if (or (nil? message) (= message ""))
                         reason-start
                         (str reason-start " " message "."))]
            {:success    false
             :error-code :http-request-failed-exception
             :reason     reason
             :exception  e}))))))


(defn ^:impure get
  "Performs an HTTP request using the GET method and based on the configuration `config` and zero or more additional
  configurations in `more-configs` and returns the response as a map. See the 'request' function for documentation.

  This method sets the ':url' key to ':get' when making the request and will overwrite that key if set in `configs`."
  [config & more-configs]
  (let [config-in (apply merge http-get-config-mods config more-configs)
        config-updated (build-check-config config-in)]
    (if-not (:success config-updated)
      config-updated
      (request (:config config-updated)))))


(defn ^:impure post
  "Performs an HTTP request using the POST method and based on the configuration in `config` and zero or more additional
  configurations in `more-configs` and returns the response as a map. See the 'request' function for documentation.

  This method sets the ':url' key to ':post' when making the request and will overwrite that key if set in `configs`."
  [config & more-configs]
  (let [config-in (apply merge http-post-config-mods config more-configs)
        config-updated (build-check-config config-in)]
    (if-not (:success config-updated)
      config-updated
      (request (:config config-updated)))))

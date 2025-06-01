(ns insilicalabs.ai.providers.openai.sse-stream
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [insilicalabs.ai.providers.openai.constants :as constants])
  (:import (com.fasterxml.jackson.core JsonParseException)
           (com.fasterxml.jackson.databind JsonMappingException)
           [java.io IOException]))


; SSE Stream - Server Sent Event Stream
;
; Used by OpenAI to stream chat completions.
;

; The ABNF for SSE streams is:
;
;   stream        = [ bom ] *event
;   event         = *( comment / field ) end-of-line
;   comment       = colon *any-char end-of-line
;   field         = 1*name-char [ colon [ space ] *any-char ] end-of-line
;   end-of-line   = ( cr lf / cr / lf )
;
; The following fn only partially handles this at the moment but this should be enough for OpenAI and an OpenAI proxy,
; which is what it was created for.


(defn- update-handler-error-response
  "Updates the response `response` for an error condition, sufficient for sending to the handler.

  The `response` is expected to be that from the HTTP request, but no requirements are placed on it for this function.

  The returned updated response is a map containing:
    - :success false           → to indicate that the request failed
    - :error-code <error code> → the error code for the failure
    - :reason <reason>         → the reason for the failure
    - :paused false            → to indicate that the model is not paused
    - :stream true             → to indicate the response was streaming
    - :stream-end true         → to indicate the end of stream
    - :exception <exception>   → the exception causing the failure; only set if calling the form using `exception`"
  ([response error-code reason]
   (update-handler-error-response response error-code reason nil))
  ([response error-code reason exception]
   (let [response (-> response
                      (assoc :success false)
                      (assoc :error-code error-code)
                      (assoc :reason reason)
                      (assoc :paused false)
                      (assoc :stream true)
                      (assoc :stream-end true))]
     (if (nil? exception)
       response
       (assoc response :exception exception)))))


(defn- create-caller-error-response
  "Creates an error response, sufficient to send to the caller.

  The error response is a map containing:
    - :success false           → to indicate that the request failed
    - :error-code <error code> → the error code for the failure
    - :reason <reason>         → the reason for the failure
    - :paused false            → to indicate that the model is not paused
    - :stream true             → to indicate the response was streaming
    - :stream-end true         → to indicate the end of stream
    - :exception <exception>   → the exception causing the failure; only set if calling the form using `exception`"
  ([error-code reason]
   (create-caller-error-response error-code reason nil))
  ([error-code reason exception]
   (let [response {:success    false
                   :error-code error-code
                   :reason     reason
                   :paused     false
                   :stream     true
                   :stream-end true}]
     (if (nil? exception)
       response
       (assoc response :exception exception)))))


(defn read-sse-stream
  "Reads the HTTP stream and processes the streaming response from an OpenAI API chat completion request and returns a
  response as a map.

  The `reader` must be a reader on the HTTP response stream to the OpenAI API chat completion request.  The `response`
  is expected to be the HTTP response to the request; no requirements are placed upon the response, but it is returned
  with augmented information (see below) as the HTTP response.  The `handler-fn` is a function to handle the response.

  Note that OpenAI's Server Sent Event (SSE) streaming implementation supports only two data types:
  'data: {JSON object}' and 'data: [DONE]'.  The data types 'event:', comment lines starting with ':', and fields
  'retry:', 'id:', and 'name:' are not supported.

  This function returns two types of responses:  one to the caller and one to handler function `handler-fn`.  Only one
  response is returned to the caller when a terminal condition is met.  The returned response to the caller helps
  facilitate the understanding of the interaction, success or failure, such as updating of chat messages as part of a
  conversation on success.  One or more responses are provided to the handler function as data or error events occur.
  Both response types are maps.  Responses are provided to the handler function first, then a response is provider to
  the caller.

  Successful responses are returned if all the following occur.  The handler function receives all responses and the
  caller receives only one response per call for the terminal condition.
    - no HTTP error occurred
    - no stream reader exception occurred
    - the content could be parsed into a valid JSON response
    - the finish_reason was:
      - 'stop'          → the model successfully generated a response; terminal condition
      - 'tools_calls'   → paused to make a tool/function call; non-terminal condition
      - 'function_call' → paused to make a legacy tool/function call; non-terminal condition
      - nil             → a chunk of data was provided from the model; non-terminal condition

  Successful responses returned to the handler function are maps with the form:
    - :success     → 'true' for a successful response
    - :stream      → 'true' for a streamed response
    - :response    → the `response` argument which should be the original HTTP response to the request
        - :data    → chunk data, which could contain 0 or more tokens; a token could be a punctuation mark or part of
                     a word or more
    - :chunk-num   → the number of chunks received thus far, starting at zero
    - :stream-end  → 'true' if a terminal condition and 'false' otherwise
    - :paused      → 'true' if paused and 'false' otherwise
    - :paused-code → the code describing the pause, set only if ':paused' is 'true, consisting of:
      - ':tool-call' → a tool/function call is being made
      - ':function-call' →  a legacy tool/function call is being made; only set if ':paused' is 'true'
    - :reason      → a string reason for why the model paused; only set if ':paused' is 'true'
    - :message     → the full message from the combined chunks; only set on a successful terminal condition

  A successful response returned to the caller is a map with the form:
    - :success     → 'true' for a successful response
    - :stream      → 'true' for a streamed response
    - :stream-end  → 'true' to indicate a terminal condition
    - :paused      → 'false' to indicate that the model is not paused
    - :message     → the full message from the combined chunks

  Unsuccessful responses are returned, both to the handler function and the caller, if any of the following occur.  All
  of these conditions are terminal.
    - an HTTP error occurred
    - a stream exception occurred
    - the content could not be parsed into a valid JSON response
    - the finish_reason was:
      - 'length'         → the token limit was reached
      - 'content_filter' → the response was blocked by the content filter
      - unrecognized     → e.g., not 'length', 'content_filter', 'stop', 'tool_calls', or 'function_call'

  Unsuccessful responses returned to the handler function are maps with the form:
    - :success    → 'false' for an unsuccessful response
    - :stream     → 'true' for a streamed response
    - :response   → the `response` argument which should be the original HTTP response to the request
    - :stream-end → 'true' to indicate a terminal condition
    - :paused     → 'false' to indicate that the model is not paused
    - :error-code → an error code indicating why the request failed; see listing of error codes below
    - :reason     → string reason why the response failed
    - :exception  → the exception that caused the failure; only set if an exception occurred and caused the failure

  An unsuccessful responses returned to the caller is a map with the form:
    - :success    → 'true' for a successful response
    - :error-code → an error code indicating why the request failed; see listing of error codes below
    - :reason     → a string reason for why the request failed
    - :exception  → the exception that caused the failure; only set if an exception occurred and caused the failure
    - :stream     → 'true' for a streamed response
    - :stream-end → 'true' to indicate a terminal condition
    - :paused     → 'false' to indicate that the model is not paused

  Error codes:
    - :stream-event-error            → an error event was received from the streamed response
    - :stream-event-unknown          → an unknown event was received from the streamed response
    - :request-failed-limit          → the response stopped due to the token limit being reached
    - :request-failed-content-filter → the response was blocked by the content filter for potentially sensitive or
                                       unsafe content

  todo: finish
    - normal usage: stop
  "
  [reader response handler-fn]
  (with-open [reader reader]
    (loop [data ""
           message-accumulator ""
           chunk-num 0]
      (let [line (try
                   (.readLine reader)
                   (catch IOException e
                     {:exception e})
                   (catch Exception e
                     {:exception e}))]
        (if (map? line)
          ;; if 'line' is a map, then an exception was thrown and caught, and 'line' contains information on the failure
          (do
            (handler-fn (update-handler-error-response response :stream-read-failed (str "The exception '" (.getName (class (:exception line))) "' occurred while reading the stream." (str (.getMessage (:exception line)))) (:exception line)))
            (create-caller-error-response :stream-read-failed (str "The exception '" (.getName (class (:exception line))) "' occurred while reading the stream." (str (.getMessage (:exception line)))) (:exception line)))
          (cond
            ; EOF
            (nil? line) (do #_nothing #_stream-finished)
            ; got data, append it and read next line
            (.startsWith line "data: ") (recur (str data (.substring line 6)) message-accumulator chunk-num)
            (.startsWith line "error: ") (let [message (str "Stream error." (.substring line 6))]
                                           (handler-fn (update-handler-error-response response :stream-event-error message))
                                           (create-caller-error-response :stream-event-error message))
            ; end of event, call handler to do something with data
            (str/blank? line) (when (not= "[DONE]" data)
                                (let [chunk-json (try
                                                   (json/parse-string data keyword)
                                                   (catch JsonParseException e
                                                     {:insilicalabs-exception e})
                                                   (catch JsonMappingException e
                                                     {:insilicalabs-exception e})
                                                   (catch IOException e
                                                     {:insilicalabs-exception e})
                                                   (catch Exception e
                                                     {:insilicalabs-exception e}))]
                                  (if (contains? chunk-json :insilicalabs-exception)
                                    (do
                                      (handler-fn (update-handler-error-response response :parse-failed (str "The exception '" (.getName (class (:insilicalabs-exception chunk-json))) "' occurred while reading the stream." (str (.getMessage (:insilicalabs-exception chunk-json)))) (:insilicalabs-exception chunk-json)))
                                      (create-caller-error-response :parse-failed (str "The exception '" (.getName (class (:insilicalabs-exception chunk-json))) "' occurred while reading the stream." (str (.getMessage (:insilicalabs-exception chunk-json)))) (:insilicalabs-exception chunk-json)))
                                    (let [finish-reason (get-in chunk-json [:choices 0 :finish_reason])
                                          response (-> response
                                                       (assoc-in [:response :data] chunk-json)
                                                       (assoc :chunk-num chunk-num))
                                          chunk-content (get-in chunk-json [:choices 0 :delta :content])
                                          updated-message-accumulator (if (some? chunk-content)
                                                                        (str message-accumulator chunk-content)
                                                                        message-accumulator)]
                                      (cond
                                        (= "length" finish-reason) (do
                                                                     (handler-fn (update-handler-error-response response constants/request-failed-limit-keyword constants/request-failed-limit-message))
                                                                     (create-caller-error-response constants/request-failed-limit-keyword constants/request-failed-limit-message))
                                        (= "content_filter" finish-reason) (do
                                                                             (handler-fn (update-handler-error-response response constants/request-failed-content-filter-keyword constants/request-failed-content-filter-message))
                                                                             (create-caller-error-response constants/request-failed-content-filter-keyword constants/request-failed-content-filter-message))
                                        (= "stop" finish-reason) (do
                                                                   (handler-fn (-> response
                                                                                   (assoc :success true)
                                                                                   (assoc :stream true)
                                                                                   (assoc :stream-end true)
                                                                                   (assoc :paused false)
                                                                                   (assoc :message updated-message-accumulator)))
                                                                   {:success    true
                                                                    :stream     true
                                                                    :stream-end true
                                                                    :paused     false
                                                                    :message    updated-message-accumulator})
                                        (= "tool_calls" finish-reason) (do
                                                                         (handler-fn (-> response
                                                                                         (assoc :success true)
                                                                                         (assoc :stream true)
                                                                                         (assoc :stream-end false)
                                                                                         (assoc :paused true)
                                                                                         (assoc :paused-code :tool-call)
                                                                                         (assoc :reason "Model paused to make a tool/function call")))
                                                                         (recur "" updated-message-accumulator (inc chunk-num)))
                                        (= "function_call" finish-reason) (do
                                                                            (handler-fn (-> response
                                                                                            (assoc :success true)
                                                                                            (assoc :stream true)
                                                                                            (assoc :stream-end false)
                                                                                            (assoc :paused true)
                                                                                            (assoc :paused-code :function-call)
                                                                                            (assoc :reason "Model paused to make a legacy tool/function call")))
                                                                            (recur "" updated-message-accumulator (inc chunk-num)))
                                        (= nil finish-reason) (do
                                                                (handler-fn (-> response
                                                                                (assoc :success true)
                                                                                (assoc :stream true)
                                                                                (assoc :stream-end false)
                                                                                (assoc :paused false)))
                                                                (recur "" updated-message-accumulator (inc chunk-num)))
                                        :else (let [message (str "Unknown stream event '" finish-reason "'.")]
                                                (handler-fn (update-handler-error-response response :stream-event-unknown message))
                                                (create-caller-error-response :stream-event-unknown message)))))))
            ; ignore all other fields in the event
            :else (recur data message-accumulator chunk-num)))))))

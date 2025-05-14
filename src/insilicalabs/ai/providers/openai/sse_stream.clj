(ns insilicalabs.ai.providers.openai.sse-stream
  (:require [cheshire.core :as json]
            [clojure.string :as str]))

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
;


(defn- update-error-response
  [response reason]
  (-> response
      (assoc :success :false)
      (assoc :reason reason)
      (assoc :paused :false)
      (assoc :stream-end true)))



;; Recovers lines comprising a JSON object starting with 'data:', stopping on '[DONE]' and erroring on 'error:'.
;; Parses the JSON, checks finish_reason:
;;   - stops on 'stop'
;;   - otherwise:
;;     - parses JSON
;;     - puts the parsed JSON at [:response :data]
;;
;; adds:
;;   - :chunk-num = int (only present if success=true)
;;   - :stream-end = true/false
;;   - :paused = true/false
;;     - :reason = reason paused (only present if paused=true; also used if success=false)
;;   - :message = string full message, only if finish_reason=true
;;
;; if successful finish_reason=stop, then return to the caller a map with :message=accumuleated messages (along with
;; typical full map, see below in "stop", but *not* :response key).  else nil.
;;
;; see notes in readme
;;
;; REQUIRED:
;;   - :success = true
;;   - :handler-fn defined
;; todo: docs
;; todo: tests
;; todo: reader exception
(defn read-sse-stream
  "Reads the HTTP stream and processes the streaming response from an OpenAI API chat completion request and returns a
  response as a map.

  The `reader` must be a reader on the HTTP response stream to the OpenAI API chat completion request.  The `response`
  is expected to be the HTTP response to the request; no requirements are based upon the response, but it is returned
  with augmented information (see below) as the HTTP response.  The `handler-fn` is a function to handle the response.

  Note that OpenAI's SSE streaming implementation supports only two data types: 'data: {JSON object}' and
  'data: [DONE]'.  The data types 'event:', comment lines starting with ':', and fields 'retry:', 'id:', and 'name:' are
  not supported.

  This function returns two types of responses:  one to the caller and one to handler function `handler-fn`.  Only one
  response is returned to the caller when a terminal condition is met.  The returned response to the caller helps
  facilitate the updating of chat messages as part of a conversation.  One or more responses are provided to the handler
  function as data or error events occur.  Both response types are maps.

  Successful responses are returned if all the following occur:
    - no HTTP error occurred
    - no stream reader exception occurred
    - the content could be parsed into a valid JSON response
    - the finish_reason was:
      - 'stop'          → the model successfully generated a response; terminal condition
      - 'tools_calls'   → paused to make a tool/function call
      - 'function_call' → paused to make a legacy tool/function call
      - nil             → a chunk of data was provided from the model

  Unsuccessful responses are returned if any of the following occur.  All of these conditions are terminal.
    - an HTTP error occurred
    - a stream exception occurred
    - the content could not be parsed into a valid JSON response
    - the finish_reason was:
      - 'length'         → the token limit was reached
      - 'content_filter' → the response was blocked by the content filter
      - unrecognized     → e.g., not 'length', 'content_filter', 'stop', 'tool_calls', or 'function_call'

  todo: finish
  "
  [reader response handler-fn]
  (with-open [reader reader]
    (loop [data ""
           message-accumulator ""
           chunk-num 0]
      (let [line (.readLine reader)]
        (cond
          ; EOF
          (nil? line) (do #_nothing #_stream-finished)
          ; got data, append it and read next line
          (.startsWith line "data: ") (recur (str data (.substring line 6)) message-accumulator chunk-num)
          (.startsWith line "error: ") (handler-fn (update-error-response response (str "Stream error." (.substring line 6))))
          ; end of event, call handler to do something with data
          (str/blank? line) (when (not= "[DONE]" data)
                              (let [chunk-json (json/parse-string data keyword)
                                    finish-reason (get-in chunk-json [:choices 0 :finish_reason])
                                    response (-> response
                                                 (assoc-in [:response :data] chunk-json)
                                                 (assoc :chunk-num chunk-num))
                                    chunk-content (get-in chunk-json [:choices 0 :delta :content])
                                    updated-message-accumulator (if (some? chunk-content)
                                                                  (str message-accumulator chunk-content)
                                                                  message-accumulator)]
                                (cond
                                  (= "length" finish-reason) (handler-fn (update-error-response response "Response stopped due to token limit being reached."))
                                  (= "content_filter" finish-reason) (handler-fn (update-error-response response "The response was blocked by the content filter for potentially sensitive or unsafe content."))
                                  (= "stop" finish-reason) (do
                                                             (handler-fn (-> response
                                                                             (assoc :stream-end true)
                                                                             (assoc :paused false)
                                                                             (assoc :message updated-message-accumulator)))
                                                             {:success true
                                                              :stream true
                                                              :stream-end true
                                                              :paused false
                                                              :message updated-message-accumulator})
                                  (= "tool_calls" finish-reason) (do
                                                                   (handler-fn (-> response
                                                                                   (assoc :stream-end false)
                                                                                   (assoc :paused true)
                                                                                   (assoc :pause-code :tool-call)
                                                                                   (assoc :reason "Model paused to make a tool/function call")))
                                                                   (recur "" updated-message-accumulator (inc chunk-num)))
                                  (= "function_call" finish-reason) (do
                                                                      (handler-fn (-> response
                                                                                      (assoc :stream-end false)
                                                                                      (assoc :paused true)
                                                                                      (assoc :pause-code :function-call)
                                                                                      (assoc :reason "Model paused to make a legacy tool/function call")))
                                                                      (recur "" updated-message-accumulator (inc chunk-num)))
                                  (= nil finish-reason) (do
                                                          (handler-fn (-> response
                                                                          (assoc :stream-end false)
                                                                          (assoc :paused false)))
                                                          (recur "" updated-message-accumulator (inc chunk-num)))
                                  :else (handler-fn (update-error-response response (str "Unrecognized finish reason '" finish-reason "'."))))))
          ; ignore all other fields in the event
          :else (recur data message-accumulator chunk-num))))))

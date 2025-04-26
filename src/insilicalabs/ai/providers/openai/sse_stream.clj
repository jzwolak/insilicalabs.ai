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
;;
;; see notes in readme
;;
;; REQUIRED:
;;   - :success = true
;;   - :handler-fn defined
(defn read-sse-stream
  [reader response handler-fn handler-config fail-point]
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
          (.startsWith line "error: ") (handler-fn (update-error-response response (str "Stream error." (.substring line 6))) handler-config)
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
                                  (= "length" finish-reason) (handler-fn (update-error-response response "Response stopped due to token limit being reached.") handler-config)
                                  (= "content_filter" finish-reason) (handler-fn (update-error-response response "The response was blocked by the content filter for potentially sensitive or unsafe content.") handler-config)
                                  (= "stop" finish-reason) (handler-fn (-> response
                                                                           (assoc :stream-end true)
                                                                           (assoc :paused false)) handler-config)
                                  (= "tool_calls" finish-reason) (do
                                                                   (handler-fn (-> response
                                                                                   (assoc :stream-end false)
                                                                                   (assoc :paused true)
                                                                                   (assoc :reason "Model paused to make a tool/function call")) handler-config)
                                                                   (recur "" updated-message-accumulator (inc chunk-num)))
                                  (= "function_call" finish-reason) (do
                                                                      (handler-fn (-> response
                                                                                      (assoc :stream-end false)
                                                                                      (assoc :paused true)
                                                                                      (assoc :reason "Model paused to make a legacy tool/function call")) handler-config)
                                                                      (recur "" updated-message-accumulator (inc chunk-num)))
                                  (= nil finish-reason) (do
                                                          (handler-fn (-> response
                                                                          (assoc :stream-end false)
                                                                          (assoc :paused false)) handler-config)
                                                          (recur "" updated-message-accumulator (inc chunk-num)))
                                  :else (handler-fn (update-error-response response (str "Unrecognized finish reason '" finish-reason "'.")) handler-config))))
          ; we ignore all other fields in the event
          :else (recur data message-accumulator chunk-num))))))


;; adds:
;;   - :chunk-num
;;   - :stream-end
(defn read-sse-stream-old1
  [reader response handler-fn handler-config fail-point]
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
          (.startsWith line "error: ") (throw (Exception. ^String (.substring line 6)))
          ; end of event, call handler to do something with data
          (str/blank? line) (when (not= "[DONE]" data)
                              (let [json-value (json/parse-string data keyword)
                                    finish-reason (get-in json-value [:choices 0 :finish_reason])]
                                (when (not= "stop" finish-reason)
                                  (let [chunk-data (get-in json-value [:choices 0 :delta :content])]
                                    (handler-fn (-> response
                                                    (assoc-in [:response :body :delta] chunk-data)
                                                    (assoc :chunk-num chunk-num)
                                                    (assoc :stream-end false))
                                                handler-config)
                                    (recur "" (str message-accumulator chunk-data) (inc chunk-num))))))
          ; we ignore all other fields in the event
          :else (recur data message-accumulator chunk-num))))))


(defn read-sse-stream-old
  [reader handler-fn]
  (with-open [reader reader]
    (loop [data ""]
      (let [line (.readLine reader)]
        (cond
          ; EOF
          (nil? line) (do #_nothing #_stream-finished)
          ; got data, append it and read next line
          (.startsWith line "data: ") (recur (str data (.substring line 6)))
          (.startsWith line "error: ") (throw (Exception. ^String (.substring line 6)))
          ; end of event, call handler to do something with data
          (str/blank? line) (when (not= "[DONE]" data)
                              (let [json-value (json/parse-string data keyword)]
                                (when (not= "stop" (get-in json-value [:choices 0 :finish_reason]))
                                  (handler-fn (get-in json-value [:choices 0 :delta :content]))
                                  (recur ""))))
          ; we ignore all other fields in the event
          :else (recur data))))))


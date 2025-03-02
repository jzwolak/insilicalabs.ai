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
; The following fn only partially handles this at the moment but this should be enough for OpenAI and our OpenAI proxy,
; which is what it was created for.
;
(defn read-sse-stream
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


(ns insilicalabs.ai.providers.openai.sse-stream-test
  (:require
    [clojure.test :refer :all]
    [insilicalabs.ai.providers.openai.sse-stream :as stream]
    [clojure.string :as str])
  (:import
    [java.io BufferedReader StringReader]
    [java.io Reader BufferedReader IOException]))


;; todo: will probably just copy this where needed so can modify values
(def ^:const json-chunk
  "{
  \"id\": \"chatcmpl-abc123\",
  \"object\": \"chat.completion.chunk\",
  \"created\": 1677858244,
  \"model\": \"gpt-4\",
  \"choices\": [
    {
      \"delta\": {
        \"content\": \"here\"
      },
      \"index\": 0,
      \"finish_reason\": null
    }
  ]
}")


(defn is-every-substring
  [string list]
  (is (every? #(str/includes? (str/lower-case string) (str/lower-case %)) list)
      (str "Expected reason substrings " list " to be in actual reason: " string)))


(defn check-perform-update-handler-error-response-test
  [actual expected]
  (is (= actual expected)))


(defn perform-update-handler-error-response-test
  ([response error-code reason expected]
   (let [update-handler-error-response #'stream/update-handler-error-response
         actual (update-handler-error-response response error-code reason)]
     (check-perform-update-handler-error-response-test actual expected)))
  ([response error-code reason expected exception]
   (let [update-handler-error-response #'stream/update-handler-error-response
         actual (update-handler-error-response response error-code reason exception)]
     (check-perform-update-handler-error-response-test actual expected))))


(deftest update-handler-error-response-test
  (testing "No exception"
    (perform-update-handler-error-response-test {:a 1} :the-err "Big error" {:a          1
                                                                             :success    false
                                                                             :paused     false
                                                                             :stream     true
                                                                             :stream-end true
                                                                             :error-code :the-err
                                                                             :reason     "Big error"}))
  (testing "With exception"
    (perform-update-handler-error-response-test {:a 1} :the-err "Big error" {:a          1
                                                                             :success    false
                                                                             :paused     false
                                                                             :stream     true
                                                                             :stream-end true
                                                                             :error-code :the-err
                                                                             :reason     "Big error"
                                                                             :exception  "Exception"} "Exception")))

(defn check-perform-create-caller-error-response-test
  [actual expected]
  (is (= actual expected)))


(defn perform-create-caller-error-response-test
  ([error-code reason expected]
   (let [create-caller-error-response #'stream/create-caller-error-response
         actual (create-caller-error-response error-code reason)]
     (check-perform-create-caller-error-response-test actual expected)))
  ([error-code reason expected exception]
   (let [create-caller-error-response #'stream/create-caller-error-response
         actual (create-caller-error-response error-code reason exception)]
     (check-perform-create-caller-error-response-test actual expected))))


(deftest create-caller-error-response-test
  (testing "No exception"
    (perform-create-caller-error-response-test :the-err "Big error" {:success    false
                                                                     :paused     false
                                                                     :stream     true
                                                                     :stream-end true
                                                                     :error-code :the-err
                                                                     :reason     "Big error"}))
  (testing "With exception"
    (perform-create-caller-error-response-test :the-err "Big error" {:success    false
                                                                     :paused     false
                                                                     :stream     true
                                                                     :stream-end true
                                                                     :error-code :the-err
                                                                     :reason     "Big error"
                                                                     :exception  "Exception"} "Exception")))


(defn get-reader
  [reader-input]
  (BufferedReader. (StringReader. reader-input)))


(defn get-bad-reader
  []
  (BufferedReader.
    (proxy [Reader] []
      (read
        ([^chars cbuf ^Integer off ^Integer len]
         (throw (IOException. "Simulated read failure"))))
      (close [] nil))))


(defn perform-failed-read-sse-stream-test
  [reader response expected-caller-response expected-handler-response]
  (let [actual-handler-response (atom nil)
        handler-fn (fn [resp] (reset! actual-handler-response resp))

        expected-caller-reason-list (:reason-list expected-caller-response)
        expected-caller-response-had-exception (contains? expected-caller-response :exception)
        expected-caller-response (-> expected-caller-response
                                     (dissoc :exception)
                                     (dissoc :reason-list))

        expected-handler-reason-list (:reason-list expected-handler-response)
        expected-handler-response-had-exception (contains? expected-handler-response :exception)
        expected-handler-response (-> expected-handler-response
                                      (dissoc :exception)
                                      (dissoc :reason-list))

        ;; do the function call
        actual-caller-response (stream/read-sse-stream reader response handler-fn)
        actual-caller-response-had-exception (contains? actual-caller-response :exception)
        actual-caller-response (dissoc actual-caller-response :exception)

        actual-caller-reason (:reason actual-caller-response)
        actual-caller-response (dissoc actual-caller-response :reason)

        actual-handler-reason (:reason @actual-handler-response)
        actual-handler-response-had-exception (contains? @actual-handler-response :exception)
        actual-handler-response (-> @actual-handler-response
                                    (dissoc :reason)
                                    (dissoc :exception))]

    (is (= expected-caller-response actual-caller-response))
    (is (= expected-caller-response-had-exception actual-caller-response-had-exception))
    (is-every-substring actual-caller-reason expected-caller-reason-list)
    (is (= expected-handler-response actual-handler-response))
    (is (= expected-handler-response-had-exception actual-handler-response-had-exception))
    (is-every-substring actual-handler-reason expected-handler-reason-list)))


(defn perform-successful-read-sse-stream-test
  [reader-input response expected-caller-response expected-handler-response]
  (let [reader (get-reader reader-input)

        actual-handler-response (atom nil)
        handler-fn (fn [resp] (reset! actual-handler-response resp))

        expected-handler-reason-list (:reason-list expected-handler-response)
        expected-handler-response (-> expected-handler-response
                                      (dissoc :reason-list))

        ;; do the function call
        actual-caller-response (stream/read-sse-stream reader response handler-fn)

        actual-handler-reason (:reason @actual-handler-response)
        actual-handler-response (-> @actual-handler-response
                                    (dissoc :reason))]

    (is (= expected-caller-response actual-caller-response))
    (is (= expected-handler-response actual-handler-response))
    (if (some? expected-handler-reason-list)
      (is-every-substring actual-handler-reason expected-handler-reason-list)
      (is (nil? actual-handler-reason)))))


(deftest read-sse-stream-test
  (testing "fail: reader exception"
    (let [reader (get-bad-reader)
          response {:response {:a 1}}
          reason-list ["exception" "IOException" "while reading the stream"]
          expected-caller-response {:success     false
                                    :error-code  :stream-read-failed
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list
                                    :exception   true}
          expected-handler-response {:response    {:a 1}
                                     :success     false
                                     :error-code  :stream-read-failed
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :reason-list reason-list
                                     :exception   true}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  (testing "fail: stream error event, where the stream provides a line:  error: <optional message>"
    (let [reader-input "error: An error occurred.\n"
          reader (get-reader reader-input)
          response {:response {:a 1}}
          reason-list ["stream error" "error occurred"]
          expected-caller-response {:success     false
                                    :error-code  :stream-event-error
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list}
          expected-handler-response {:response    {:a 1}
                                     :success     false
                                     :error-code  :stream-event-error
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :reason-list reason-list}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  (testing "fail: json parse error"
    (let [reader-input "data: {parse error}\n\n"
          reader (get-reader reader-input)
          response {:response {:a 1}}
          reason-list ["JsonParseException"]
          expected-caller-response {:success     false
                                    :error-code  :parse-failed,
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list
                                    :exception   true}
          expected-handler-response {:response    {:a 1}
                                     :success     false
                                     :error-code  :parse-failed
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :reason-list reason-list
                                     :exception   true}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  (testing "fail: finish_reason 'length' error"
    (let [reader-input "data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858247,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{},\"index\":0, \"finish_reason\":\"length\"}]}\n\n"
          reader (get-reader reader-input)
          response {:response {:a 1}}
          reason-list ["response" "stopped" "token" "limit"]
          expected-caller-response {:success     false
                                    :error-code  :request-failed-limit
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list}
          expected-handler-response {:response    {:a    1
                                                   :data {:id      "chatcmpl-abc123"
                                                          :object  "chat.completion.chunk"
                                                          :created 1677858247
                                                          :model   "gpt-4"
                                                          :choices [{:delta         {}
                                                                     :index         0
                                                                     :finish_reason "length"}]}}
                                     :success     false
                                     :error-code  :request-failed-limit
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :chunk-num   0
                                     :reason-list reason-list}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  (testing "fail: finish_reason 'content_filter' error"
    (let [reader-input "data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858247,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{},\"index\":0, \"finish_reason\":\"content_filter\"}]}\n\n"
          reader (get-reader reader-input)
          response {:response {:a 1}}
          reason-list ["response" "blocked" "content" "filter" "sensitive" "unsafe"]
          expected-caller-response {:success     false
                                    :error-code  :request-failed-content-filter
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list}
          expected-handler-response {:response    {:a    1
                                                   :data {:id      "chatcmpl-abc123"
                                                          :object  "chat.completion.chunk"
                                                          :created 1677858247
                                                          :model   "gpt-4"
                                                          :choices [{:delta         {}
                                                                     :index         0
                                                                     :finish_reason "content_filter"}]}}
                                     :success     false
                                     :error-code  :request-failed-content-filter
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :chunk-num   0
                                     :reason-list reason-list}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  (testing "fail: finish_reason unknown error"
    (let [reader-input "data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858247,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{},\"index\":0, \"finish_reason\":\"blah\"}]}\n\n"
          reader (get-reader reader-input)
          response {:response {:a 1}}
          reason-list ["unknown" "stream" "event"]
          expected-caller-response {:success     false
                                    :error-code  :stream-event-unknown
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list}
          expected-handler-response {:response    {:a    1
                                                   :data {:id      "chatcmpl-abc123"
                                                          :object  "chat.completion.chunk"
                                                          :created 1677858247
                                                          :model   "gpt-4"
                                                          :choices [{:delta         {}
                                                                     :index         0
                                                                     :finish_reason "blah"}]}}
                                     :success     false
                                     :error-code  :stream-event-unknown
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :chunk-num   0
                                     :reason-list reason-list}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  (testing "pause: tool_calls"
    (let [reader-input "data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858247,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{},\"index\":0, \"finish_reason\":\"tool_calls\"}]}\n\n"
          response {:response {:a 1}}
          expected-caller-response nil
          expected-handler-response {:response    {:a    1
                                                   :data {:id      "chatcmpl-abc123"
                                                          :object  "chat.completion.chunk"
                                                          :created 1677858247
                                                          :model   "gpt-4"
                                                          :choices [{:delta         {}
                                                                     :index         0
                                                                     :finish_reason "tool_calls"}]}}
                                     :success     true
                                     :paused      true
                                     :stream      true
                                     :stream-end  false
                                     :chunk-num   0
                                     :paused-code :tool-call
                                     :reason-list ["model" "paused" "tool" "function" "call"]}]
      (perform-successful-read-sse-stream-test reader-input response expected-caller-response expected-handler-response)))
  (testing "pause: function_call"
    (let [reader-input "data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858247,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{},\"index\":0, \"finish_reason\":\"function_call\"}]}\n\n"
          response {:response {:a 1}}
          expected-caller-response nil
          expected-handler-response {:response    {:a    1
                                                   :data {:id      "chatcmpl-abc123"
                                                          :object  "chat.completion.chunk"
                                                          :created 1677858247
                                                          :model   "gpt-4"
                                                          :choices [{:delta         {}
                                                                     :index         0
                                                                     :finish_reason "function_call"}]}}
                                     :success     true
                                     :paused      true
                                     :stream      true
                                     :stream-end  false
                                     :chunk-num   0
                                     :paused-code :function-call
                                     :reason-list ["model" "paused" "tool" "function" "call" "legacy"]}]
      (perform-successful-read-sse-stream-test reader-input response expected-caller-response expected-handler-response)))
  (testing "success: single chunk, finish_reason 'null'"
    (let [reader-input "data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858244,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"a chunk\"},\"index\":0,\"finish_reason\":null}]}\n\n"
          response {:response {:a 1}}
          expected-caller-response nil
          expected-handler-response {:response   {:a    1
                                                  :data {:id      "chatcmpl-abc123"
                                                         :object  "chat.completion.chunk"
                                                         :created 1677858244
                                                         :model   "gpt-4"
                                                         :choices [{:delta         {:content "a chunk"}
                                                                    :index         0
                                                                    :finish_reason nil}]}}
                                     :success    true
                                     :paused     false
                                     :stream     true
                                     :stream-end false
                                     :chunk-num  0}]
      (perform-successful-read-sse-stream-test reader-input response expected-caller-response expected-handler-response)))
  (testing "success: two chunks (finish_reason 'null'), then done with 3rd chunk w/ finish_reason 'stop'"
    (let [reader-input "data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858244,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\"A\"},\"index\":0,\"finish_reason\":null}]}\n
data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858244,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{\"content\":\" chunk\"},\"index\":0,\"finish_reason\":null}]}\n
data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"created\":1677858244,\"model\":\"gpt-4\",\"choices\":[{\"delta\":{},\"index\":0,\"finish_reason\":\"stop\"}]}\n\n"
          response {:response {:a 1}}
          expected-caller-response {:success    true
                                    :stream     true
                                    :stream-end true
                                    :paused     false
                                    :message    "A chunk"}
          expected-handler-response {:response   {:a    1
                                                  :data {:id      "chatcmpl-abc123"
                                                         :object  "chat.completion.chunk"
                                                         :created 1677858244
                                                         :model   "gpt-4"
                                                         :choices [{:delta         {}
                                                                    :index         0
                                                                    :finish_reason "stop"}]}}
                                     :success    true
                                     :paused     false
                                     :stream     true
                                     :stream-end true
                                     :chunk-num  2
                                     :message    "A chunk"}]
      (perform-successful-read-sse-stream-test reader-input response expected-caller-response expected-handler-response))))


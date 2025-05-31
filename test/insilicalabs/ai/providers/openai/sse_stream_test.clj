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


;; todo: will probably just copy this where needed so can modify values
(def json-chunk-stop
  "{
  \"id\": \"chatcmpl-abc123\",
  \"object\": \"chat.completion.chunk\",
  \"created\": 1677858247,
  \"model\": \"gpt-4\",
  \"choices\": [
    {
      \"delta\": {},
      \"index\": 0,
      \"finish_reason\": \"stop\"
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


(deftest read-sse-stream-test
  (testing "reader exception"
    (let [reader (get-bad-reader)
          response {:a 1}
          reason-list ["exception" "IOException" "while reading the stream"]
          expected-caller-response {:success     false
                                    :error-code  :stream-read-failed
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list
                                    :exception   true}
          expected-handler-response {:a           1
                                     :success     false
                                     :error-code  :stream-read-failed
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :reason-list reason-list
                                     :exception   true}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  (testing "stream error event, where the stream provides a line:  error: <optional message>"
    (let [reader-input "error: An error occurred.\n"
          reader (get-reader reader-input)
          response {:a 1}
          reason-list ["stream error" "error occurred"]
          expected-caller-response {:success     false
                                    :error-code  :stream-event-error
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list}
          expected-handler-response {:a           1
                                     :success     false
                                     :error-code  :stream-event-error
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :reason-list reason-list}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  (testing "json parse error"
    (let [reader-input "data: {parse error}\n\n"
          reader (get-reader reader-input)
          response {:a 1}
          reason-list ["JsonParseException"]
          expected-caller-response {:success     false
                                    :error-code  :parse-failed,
                                    :paused      false
                                    :stream      true
                                    :stream-end  true
                                    :reason-list reason-list
                                    :exception   true}
          expected-handler-response {:a           1
                                     :success     false
                                     :error-code  :parse-failed,
                                     :paused      false
                                     :stream      true
                                     :stream-end  true
                                     :reason-list reason-list
                                     :exception   true}]
      (perform-failed-read-sse-stream-test reader response expected-caller-response expected-handler-response)))
  )


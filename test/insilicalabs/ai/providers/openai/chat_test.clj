(ns insilicalabs.ai.providers.openai.chat-test
  (:require
    [clojure.test :refer :all]
    [insilicalabs.ai.providers.openai.chat :as chat]
    [insilicalabs.ai.http :as http]
    [clojure.string :as str]))


(defn is-every-substring
  [string list]
  (is (every? #(str/includes? (str/lower-case string) (str/lower-case %)) list)
      (str "Expected reason substrings " list " to be in actual reason: " string)))



(defn check-prepared-request-test
  [prepared-request expected]
  (is (= prepared-request expected)))


(defn perform-create-prepared-request-test
  ([request-config expected]
   (check-prepared-request-test (chat/create-prepared-request request-config) expected))
  ([request-config response-config expected]
   (check-prepared-request-test (chat/create-prepared-request request-config response-config) expected))
  ([auth-config http-config request-config response-config expected]
   (check-prepared-request-test (chat/create-prepared-request auth-config http-config request-config response-config) expected)))

(defn handler
  [])

(deftest create-prepared-request-test
  ;;
  ;; arity [request-config]
  (testing "arity [request-config]"
    (perform-create-prepared-request-test {:model "model-123"} {:prepared-request {:url          chat/chat-completions-url-default
                                                                                   :content-type :json
                                                                                   :accept       :json}
                                                                :auth-config      {}
                                                                :request-config   {:model "model-123"}
                                                                :response-config  {:stream false}}))
  ;;
  ;; arity [request-config response-config]
  (testing "arity [request-config response-config]: response-config: empty"
    (perform-create-prepared-request-test {:model "model-123"} {} {:prepared-request {:url          chat/chat-completions-url-default
                                                                                      :content-type :json
                                                                                      :accept       :json}
                                                                   :auth-config      {}
                                                                   :request-config   {:model "model-123"}
                                                                   :response-config  {:stream false}}))
  (testing "arity [request-config response-config]: response-config: handler-fn"
    (perform-create-prepared-request-test {:model "model-123"} {:handler-fn handler} {:prepared-request {:url          chat/chat-completions-url-default
                                                                                                         :content-type :json
                                                                                                         :accept       :json}
                                                                                      :auth-config      {}
                                                                                      :request-config   {:model "model-123"}
                                                                                      :response-config  {:stream     false
                                                                                                         :handler-fn handler}}))
  (testing "arity [request-config response-config]: response-config: handler-fn, stream=false"
    (perform-create-prepared-request-test {:model "model-123"} {:handler-fn handler
                                                                :stream     false} {:prepared-request {:url          chat/chat-completions-url-default
                                                                                                       :content-type :json
                                                                                                       :accept       :json}
                                                                                    :auth-config      {}
                                                                                    :request-config   {:model "model-123"}
                                                                                    :response-config  {:stream     false
                                                                                                       :handler-fn handler}}))
  (testing "arity [request-config response-config]: response-config: handler-fn, stream=true"
    (perform-create-prepared-request-test {:model "model-123"} {:handler-fn handler
                                                                :stream     true} {:prepared-request {:url          chat/chat-completions-url-default
                                                                                                      :content-type :json
                                                                                                      :accept       :json
                                                                                                      :as           :reader}
                                                                                   :auth-config      {}
                                                                                   :request-config   {:model  "model-123"
                                                                                                      :stream true}
                                                                                   :response-config  {:stream     true
                                                                                                      :handler-fn handler}}))
  ;;
  ;; arity [auth-config http-config request-config response-config]
  (testing "arity [auth-config http-config request-config response-config]: only required configurations.  request-config: model"
    (perform-create-prepared-request-test {} {} {:model "model-123"} {} {:prepared-request {:url          chat/chat-completions-url-default
                                                                                            :content-type :json
                                                                                            :accept       :json}
                                                                         :auth-config      {}
                                                                         :request-config   {:model "model-123"}
                                                                         :response-config  {:stream false}}))
  ;; arity [auth-config http-config request-config response-config]
  (testing "arity [auth-config http-config request-config response-config]: set all configurations, stream=true"
    (perform-create-prepared-request-test {:api-org  "my org"
                                           :api-proj "my proj"}
                                          {:socket-timeout     6000
                                           :connection-timeout 7000}
                                          {:model "model-123"}
                                          {:handler-fn handler
                                           :stream     true}
                                          {:prepared-request {:url                chat/chat-completions-url-default
                                                              :content-type       :json
                                                              :accept             :json
                                                              :as                 :reader
                                                              :socket-timeout     6000
                                                              :connection-timeout 7000}
                                           :auth-config      {:api-org  "my org"
                                                              :api-proj "my proj"}
                                           :request-config   {:model  "model-123"
                                                              :stream true}
                                           :response-config  {:stream     true
                                                              :handler-fn handler}}))
  (testing "arity [auth-config http-config request-config response-config]: set all configurations, stream=false"
    (perform-create-prepared-request-test {:api-org  "my org"
                                           :api-proj "my proj"}
                                          {:socket-timeout     6000
                                           :connection-timeout 7000}
                                          {:model "model-123"}
                                          {:handler-fn handler
                                           :stream     false}
                                          {:prepared-request {:url                chat/chat-completions-url-default
                                                              :content-type       :json
                                                              :accept             :json
                                                              :socket-timeout     6000
                                                              :connection-timeout 7000}
                                           :auth-config      {:api-org  "my org"
                                                              :api-proj "my proj"}
                                           :request-config   {:model "model-123"}
                                           :response-config  {:stream     false
                                                              :handler-fn handler}}))
  (testing "arity [auth-config http-config request-config response-config]: set all configurations, omit stream"
    (perform-create-prepared-request-test {:api-org  "my org"
                                           :api-proj "my proj"}
                                          {:socket-timeout     6000
                                           :connection-timeout 7000}
                                          {:model "model-123"}
                                          {:handler-fn handler}
                                          {:prepared-request {:url                chat/chat-completions-url-default
                                                              :content-type       :json
                                                              :accept             :json
                                                              :socket-timeout     6000
                                                              :connection-timeout 7000}
                                           :auth-config      {:api-org  "my org"
                                                              :api-proj "my proj"}
                                           :request-config   {:model "model-123"}
                                           :response-config  {:stream     false
                                                              :handler-fn handler}})))


(deftest create-prepared-request-from-config-test
  (testing "all empty"
    (let [config {:auth-config     {}
                  :http-config     {}
                  :request-config  {}
                  :response-config {}}
          prepared-request (chat/create-prepared-request-from-config config)]
      (check-prepared-request-test prepared-request {:prepared-request {:url          chat/chat-completions-url-default
                                                                        :content-type :json
                                                                        :accept       :json}
                                                     :auth-config      {}
                                                     :request-config   {}
                                                     :response-config  {:stream false}})))
  (testing "all configurations set"
    (let [config {:auth-config     {:api-org  "my org"
                                    :api-proj "my proj"}
                  :http-config     {:socket-timeout     6000
                                    :connection-timeout 7000}
                  :request-config  {:model "model-123"}
                  :response-config {:handler-fn handler
                                    :stream     true}}
          prepared-request (chat/create-prepared-request-from-config config)]
      (check-prepared-request-test prepared-request {:prepared-request {:url                chat/chat-completions-url-default
                                                                        :content-type       :json
                                                                        :accept             :json
                                                                        :as                 :reader
                                                                        :socket-timeout     6000
                                                                        :connection-timeout 7000}
                                                     :auth-config      {:api-org  "my org"
                                                                        :api-proj "my proj"}
                                                     :request-config   {:model  "model-123"
                                                                        :stream true}
                                                     :response-config  {:stream     true
                                                                        :handler-fn handler}}))))


(defn perform-create-headers-test
  [auth-config expected]
  (let [create-headers #'chat/create-headers
        actual (create-headers auth-config)]
    (is (= actual expected))))


(deftest create-headers-test
  (testing "API key only"
    (perform-create-headers-test {:api-key "ABCD1234"} {"Authorization" "Bearer ABCD1234"}))
  (testing "all params"
    (perform-create-headers-test {:api-key  "ABCD1234"
                                  :api-proj "my proj"
                                  :api-org  "my org"}
                                 {"Authorization"       "Bearer ABCD1234"
                                  "OpenAI-Organization" "my org"
                                  "OpenAI-Project"      "my proj"})))


(defn check-create-messages-test
  [messages expected]
  (is (= messages expected)))


(defn perform-create-messages-test
  ([system-message user-message expected]
   (let [create-messages #'chat/create-messages
         messages (create-messages system-message user-message)]
     (check-create-messages-test messages expected)))
  ([messages system-message user-message expected]
   (let [create-messages #'chat/create-messages
         messages (create-messages messages system-message user-message)]
     (check-create-messages-test messages expected))))

(deftest create-messages-test
  ;;
  ;; arity [system-message user-message]
  (testing "arity [system-message user-message]: nil, nil"
    (perform-create-messages-test nil nil []))
  (testing "arity [system-message user-message]: msg, nil"
    (perform-create-messages-test "You are helpful assistant" nil [{:role "system", :content "You are helpful assistant"}]))
  (testing "arity [system-message user-message]: nil, msg"
    (perform-create-messages-test nil "Hello" [{:role "user", :content "Hello"}]))
  (testing "arity [system-message user-message]: msg, msg"
    (perform-create-messages-test "You are helpful assistant" "Hello" [{:role "system", :content "You are helpful assistant"}
                                                                       {:role "user", :content "Hello"}]))
  ;;
  ;; arity [messages system-message user-message]
  (testing "arity [messages system-message user-message]: nil, nil, nil"
    (perform-create-messages-test nil nil nil []))
  (testing "arity [messages system-message user-message]: [], nil, nil"
    (perform-create-messages-test [] nil nil []))
  (testing "arity [messages system-message user-message]: [], msg, nil"
    (perform-create-messages-test [] "You are helpful assistant" nil [{:role "system", :content "You are helpful assistant"}]))
  (testing "arity [messages system-message user-message]: [], nil, msg"
    (perform-create-messages-test [] nil "Hello" [{:role "user", :content "Hello"}]))
  (testing "arity [messages system-message user-message]: [], msg, msg"
    (perform-create-messages-test [] "You are helpful assistant" "Hello" [{:role "system", :content "You are helpful assistant"}
                                                                          {:role "user", :content "Hello"}]))
  (testing "arity [messages system-message user-message]: [msg], nil, msg"
    (perform-create-messages-test [{:role "system", :content "You are helpful assistant"}] nil "Hello" [{:role "system", :content "You are helpful assistant"}
                                                                                                        {:role "user", :content "Hello"}])))

(defn perform-create-messages-from-messages-or-user-message-test
  [messages-or-user-message expected]
  (let [create-messages-from-messages-or-user-message #'chat/create-messages-from-messages-or-user-message]
    (is (= (create-messages-from-messages-or-user-message messages-or-user-message) expected))))


(deftest create-messages-from-messages-or-user-message-test
  (testing "nil"
    (perform-create-messages-from-messages-or-user-message-test nil []))
  (testing "string"
    (perform-create-messages-from-messages-or-user-message-test "Hello" [{:role "user", :content "Hello"}]))
  (testing "[]"
    (perform-create-messages-from-messages-or-user-message-test [] []))
  (testing "[msg]"
    (perform-create-messages-from-messages-or-user-message-test [{:role "system", :content "You are helpful assistant"}] [{:role "system", :content "You are helpful assistant"}])))


(defn check-get-response-as-string-test
  [actual expected]
  (is (= actual expected)))


(defn perform-get-response-as-string-test
  ([response expected]
   (check-get-response-as-string-test (chat/get-response-as-string response) expected))
  ([response n expected]
   (check-get-response-as-string-test (chat/get-response-as-string response n) expected)))


(defn non-streaming-response
  ([content]
   (non-streaming-response content "Another content"))
  ([content1 content2]
   {:success  true
    :response {:body {:choices [{:message {:content content1}}
                                {:message {:content content2}}]}}}))


(defn streaming-response
  [content]
  {:success  true
   :stream   true
   :response {:data {:choices [{:delta {:content content}}]}}})


(deftest get-response-as-string-test
  ;;
  ;; [response]
  (testing "arity [response]: request not successful"
    (perform-get-response-as-string-test {:success false} nil))
  (testing "arity [response]: non-streaming, no content"
    (perform-get-response-as-string-test (non-streaming-response nil) ""))
  (testing "arity [response]: non-streaming, has content"
    (perform-get-response-as-string-test (non-streaming-response "Content") "Content"))
  (testing "arity [response]: streaming, no content"
    (perform-get-response-as-string-test (streaming-response nil) ""))
  (testing "arity [response]: streaming, has content"
    (perform-get-response-as-string-test (streaming-response "Content") "Content"))
  ;;
  ;; [response n]
  (testing "arity [response]: request not successful"
    (perform-get-response-as-string-test {:success false} 0 nil))
  (testing "arity [response]: non-streaming, no content, index 0"
    (perform-get-response-as-string-test (non-streaming-response nil) 0 ""))
  (testing "arity [response]: non-streaming, has content, index 0"
    (perform-get-response-as-string-test (non-streaming-response "Content") 0 "Content"))
  (testing "arity [response]: non-streaming, no content, index 1"
    (perform-get-response-as-string-test (non-streaming-response nil nil) 1 ""))
  (testing "arity [response]: non-streaming, has content, index 1"
    (perform-get-response-as-string-test (non-streaming-response "Content") 1 "Another content"))
  (testing "arity [response]: streaming, no content, index 0"
    (perform-get-response-as-string-test (streaming-response nil) 0 ""))
  (testing "arity [response]: streaming, has content, index 0"
    (perform-get-response-as-string-test (streaming-response "Content") 0 "Content"))
  (testing "arity [response]: streaming, no content, index 1"
    (perform-get-response-as-string-test (streaming-response nil) 1 ""))
  (testing "arity [response]: streaming, has content, index 1"
    (perform-get-response-as-string-test (streaming-response "Content") 1 "")))


(defn perform-get-response-as-string-vector-test
  [response expected]
  (let [actual (chat/get-response-as-string-vector response)]
    (is (= actual expected))))


(deftest get-response-as-string-vector-test
  ;;
  ;; general
  (testing "response not successful"
    (perform-get-response-as-string-vector-test {:success false} nil))
  ;;
  ;; non-streaming
  (testing "non-streaming, 0 entries"
    (perform-get-response-as-string-vector-test {:success  true
                                                 :response {:body {:choices [{:message {}}]}}} [""]))
  (testing "non-streaming, 1 entry nil"
    (perform-get-response-as-string-vector-test {:success  true
                                                 :response {:body {:choices [{:message {:content nil}}]}}} [""]))
  (testing "non-streaming, 1 entry non-nil"
    (perform-get-response-as-string-vector-test {:success  true
                                                 :response {:body {:choices [{:message {:content "hi"}}]}}} ["hi"]))
  (testing "non-streaming, multiple entries"
    (perform-get-response-as-string-vector-test {:success  true
                                                 :response {:body {:choices [{:message {:content "hi"}}
                                                                             {:message {:content nil}}
                                                                             {:message {:content "hello"}}]}}} ["hi" "" "hello"]))
  ;;
  ;; streaming
  (testing "non-streaming, 0 entries"
    (perform-get-response-as-string-vector-test {:success  true
                                                 :stream   true
                                                 :response {:data {:choices [{:delta {}}]}}} [""]))
  (testing "non-streaming, 1 entry nil"
    (perform-get-response-as-string-vector-test {:success  true
                                                 :stream   true
                                                 :response {:data {:choices [{:delta {:content nil}}]}}} [""]))
  (testing "non-streaming, 1 entry non-nil"
    (perform-get-response-as-string-vector-test {:success  true
                                                 :stream   true
                                                 :response {:data {:choices [{:delta {:content "hi"}}]}}} ["hi"])))


(defn perform-complete-request-test
  [prepared-request expected]
  (with-redefs [http/post (fn [x] x)]
    (let [complete-request #'chat/complete-request
          actual (complete-request prepared-request)]
      (if (:success expected)
        (let [url-list-expected (:url-list expected)
              expected (-> expected
                           (dissoc :url-list)
                           (dissoc :success))
              url-actual (:url actual)
              actual (dissoc actual :url)]
          (is (= actual expected))
          (is-every-substring url-actual url-list-expected))
        (let [reason-actual (:reason actual)
              actual (dissoc actual :reason)
              reason-list (:reason-list expected)
              expected (dissoc expected :reason-list)]
          (is (= (actual expected)))
          (is-every-substring reason-actual reason-list))))))


(deftest complete-request-test
  (testing "invalid: no api-key"
    (let [prepared-request (chat/create-prepared-request {:model "the-model"})
          prepared-request (-> prepared-request
                               (assoc-in [:request-config :messages] []))]
      (perform-complete-request-test prepared-request {:success     false
                                                       :error-code  :request-config-not-map
                                                       :reason-list ["Request config" ":api-key" ":auth-config"]})))
  (testing "invalid: api-proj but no api-org"
    (let [prepared-request (chat/create-prepared-request {:api-proj "myproj"} {} {:model "the-model"} {})
          prepared-request (-> prepared-request
                               (assoc-in [:auth-config :api-key] "ABC123")
                               (assoc-in [:request-config :messages] []))]
      (perform-complete-request-test prepared-request {:success     false
                                                       :error-code  :request-config-api-proj-org
                                                       :reason-list ["Request config" ":api-key" ":api-org" ":auth-config"]})))
  (testing "invalid: api-org but no api-proj"
    (let [prepared-request (chat/create-prepared-request {:api-org "myorg"} {} {:model "the-model"} {})
          prepared-request (-> prepared-request
                               (assoc-in [:auth-config :api-key] "ABC123")
                               (assoc-in [:request-config :messages] []))]
      (perform-complete-request-test prepared-request {:success     false
                                                       :error-code  :request-config-api-proj-org
                                                       :reason-list ["Request config" ":api-key" ":api-org" ":auth-config"]})))
  (testing "invalid: no model"
    (let [prepared-request (chat/create-prepared-request {})
          prepared-request (-> prepared-request
                               (assoc-in [:auth-config :api-key] "ABC123")
                               (assoc-in [:request-config :messages] []))]
      (perform-complete-request-test prepared-request {:success     false
                                                       :error-code  :request-config-model-missing
                                                       :reason-list ["Request config" ":model" ":request-config"]})))
  (testing "invalid: no messages"
    (let [prepared-request (chat/create-prepared-request {:model "the-model"})
          prepared-request (-> prepared-request
                               (assoc-in [:auth-config :api-key] "ABC123"))]
      (perform-complete-request-test prepared-request {:success     false
                                                       :error-code  :request-config-messages-missing
                                                       :reason-list ["Request config" ":messages" ":request-config"]})))
  (testing "valid: api-key, model, messages"
    (let [prepared-request (chat/create-prepared-request {:model "the-model"})
          prepared-request (-> prepared-request
                               (assoc-in [:auth-config :api-key] "ABC123")
                               (assoc-in [:request-config :messages] []))]
      (perform-complete-request-test prepared-request {:success      true
                                                       :url-list     ["https://api.openai.com" "chat/completions"]
                                                       :content-type :json
                                                       :accept       :json
                                                       :headers      {"Authorization" "Bearer ABC123"}
                                                       :body         "{\"model\":\"the-model\",\"messages\":[]}"})))
  (testing "valid: api-key, api-proj, api-org, model, messages"
    (let [prepared-request (chat/create-prepared-request {:api-org "myorg" :api-proj "myproj"} {} {:model "the-model"} {})
          prepared-request (-> prepared-request
                               (assoc-in [:auth-config :api-key] "ABC123")
                               (assoc-in [:request-config :messages] []))]
      (perform-complete-request-test prepared-request {:success      true
                                                       :url-list     ["https://api.openai.com" "chat/completions"]
                                                       :content-type :json
                                                       :accept       :json
                                                       :headers      {"Authorization" "Bearer ABC123", "OpenAI-Organization" "myorg", "OpenAI-Project" "myproj"}
                                                       :body         "{\"model\":\"the-model\",\"messages\":[]}"}))))


(deftest change-response-to-unsuccessful-test
  (let [change-response-to-unsuccessful #'chat/change-response-to-unsuccessful]
    (is (= (change-response-to-unsuccessful {:success true} :request-failed-limit "Because") {:success    false
                                                                                              :error-code :request-failed-limit
                                                                                              :reason     "Because"}))))


(defn perform-check-response-errors-test
  [response expected]
  (let [check-response-errors #'chat/check-response-errors
        actual (check-response-errors response)
        actual-reason (:reason actual)
        actual-adjusted (dissoc actual :reason)
        expected-reason-list (:reason-list expected)
        expected-adjusted (dissoc expected :reason-list)]
    (is (= actual-adjusted expected-adjusted))
    (is-every-substring actual-reason expected-reason-list)))


(deftest check-response-errors-test
  (testing "no response errors"
    (perform-check-response-errors-test {:response {:finish_reason "ok"}} {:response {:finish_reason "ok"}}))
  (testing "err: length"
    (perform-check-response-errors-test {:response {:finish_reason "length"}} {:response    {:finish_reason "length"}
                                                                               :success     false
                                                                               :error-code  :request-failed-limit
                                                                               :reason-list ["token" "limit"]}))
  (testing "err: content_filter"
    (perform-check-response-errors-test {:response {:finish_reason "content_filter"}} {:response    {:finish_reason "content_filter"}
                                                                                       :success     false
                                                                                       :error-code  :request-failed-content-filter
                                                                                       :reason-list ["blocked" "content" "filter"]})))

(defn perform-normalize-string-to-kebab-keyword-test
  [property expected]
  (let [normalize-string-to-kebab-keyword #'chat/normalize-string-to-kebab-keyword]
    (is (= (normalize-string-to-kebab-keyword property) expected))))


(deftest normalize-string-to-kebab-keyword-test
  (testing "no change, already kebab: keyword no dash"
    (perform-normalize-string-to-kebab-keyword-test :hello :hello))
  (testing "no change, already kebab: keyword w/ dash"
    (perform-normalize-string-to-kebab-keyword-test :hello-there :hello-there))
  (testing "keyword camel w/ dash"
    (perform-normalize-string-to-kebab-keyword-test :Hello-There :hello-there))
  (testing "keyword camel"
    (perform-normalize-string-to-kebab-keyword-test :HelloThere :hellothere))
  (testing "string camel w/ dash"
    (perform-normalize-string-to-kebab-keyword-test "Hello-There" :hello-there))
  (testing "string camel"
    (perform-normalize-string-to-kebab-keyword-test "HelloThere" :hellothere)))


(defn perform-normalize-all-string-properties-to-kebab-keyword-test
  [headers expected]
  (let [normalize-all-string-properties-to-kebab-keyword #'chat/normalize-all-string-properties-to-kebab-keyword]
    (is (= (normalize-all-string-properties-to-kebab-keyword headers) expected))))


(deftest normalize-all-string-properties-to-kebab-keyword-test
  (testing "no change, already kebab: no dash"
    (perform-normalize-all-string-properties-to-kebab-keyword-test {:greeting "hello"
                                                                    :language "english"}
                                                                   {:greeting "hello"
                                                                    :language "english"}))
  (testing "no change, already kebab: w/ dash"
    (perform-normalize-all-string-properties-to-kebab-keyword-test {:greeting-here "hello"
                                                                    :language-here "english"}
                                                                   {:greeting-here "hello"
                                                                    :language-here "english"}))
  (testing "string camel w/ dash"
    (perform-normalize-all-string-properties-to-kebab-keyword-test {"Greeting-Here" "hello"
                                                                    "Language-Here" "english"}
                                                                   {:greeting-here "hello"
                                                                    :language-here "english"}))
  (testing "string camel"
    (perform-normalize-all-string-properties-to-kebab-keyword-test {"GreetingHere" "hello"
                                                                    "LanguageHere" "english"}
                                                                   {:greetinghere "hello"
                                                                    :languagehere "english"})))


(defn perform-get-contents-test
  [response expected]
  (let [get-contents #'chat/get-contents
        actual (get-contents response)]
    (is (= actual expected))))


(deftest get-contents-test
  (testing "1 content item"
    (perform-get-contents-test {:success  true
                                :response {:body {:choices [{:message {:content "hi"}}]}}} ["hi"]))
  (testing "multiple content items"
    (perform-get-contents-test {:success  true
                                :response {:body {:choices [{:message {:content "hi"}}
                                                            {:message {:content "hello"}}
                                                            {:message {:content "hey"}}]}}} ["hi" "hello" "hey"])))
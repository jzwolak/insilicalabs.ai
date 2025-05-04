(ns insilicalabs.ai.providers.openai.chat-test
  (:require
    [clojure.test :refer :all]
    [insilicalabs.ai.providers.openai.chat :as chat]))


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
  ;; general
  (testing "arity [response]: request not successful"
    (perform-get-response-as-string-test {:success false} nil))
  (testing "arity [response]: request not successful"
    (perform-get-response-as-string-test {:success false} nil))
  ;;
  ;; [response]
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
    (perform-get-response-as-string-test (streaming-response "Content") 1 ""))
  )
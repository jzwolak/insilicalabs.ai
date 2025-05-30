(ns insilicalabs.ai.providers.openai.sse-stream-test
  (:require
    [clojure.test :refer :all]
    [insilicalabs.ai.providers.openai.sse-stream :as stream]
    [clojure.string :as str]))


(defn is-every-substring
  [string list]
  (is (every? #(str/includes? (str/lower-case string) (str/lower-case %)) list)
      (str "Expected reason substrings " list " to be in actual reason: " string)))


(defn perform-update-handler-error-response-test
  [response error-code reason expected]
  (let [update-handler-error-response #'stream/update-handler-error-response
        actual (update-handler-error-response response error-code reason)]
    (is (= actual expected))))


(deftest update-handler-error-response-test
  (testing "Update"
    (perform-update-handler-error-response-test {:a 1} :the-err "Big error" {:a          1
                                                                             :success    false
                                                                             :paused     false
                                                                             :stream     true
                                                                             :stream-end true
                                                                             :error-code :the-err
                                                                             :reason     "Big error"})))


(defn perform-create-caller-error-response-test
  [error-code reason expected]
  (let [create-caller-error-response #'stream/create-caller-error-response
        actual (create-caller-error-response error-code reason)]
    (is (= actual expected))))


(deftest create-caller-error-response-test
  (testing "Create"
    (perform-create-caller-error-response-test :the-err "Big error" {:success    false
                                                                     :paused     false
                                                                     :stream     true
                                                                     :stream-end true
                                                                     :error-code :the-err
                                                                     :reason     "Big error"})))
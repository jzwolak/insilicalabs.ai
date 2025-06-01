;; Copyright © 2025 Jason Zwolak
;;
;; This source code is licensed under the MIT License.
;; See the LICENSE file in the root directory of this source tree for details.


(ns insilicalabs.ai.providers.openai.config-test
  (:require
    [clojure.test :refer :all]
    [insilicalabs.ai.providers.openai.config :as config]))


(defn perform-create-auth-config-test
  [api-proj api-org expected]
  (let [auth-config (config/create-auth-config api-proj api-org)]
    (is (= auth-config expected))))


(deftest create-auth-config-test
  (testing "all arguments"
    (perform-create-auth-config-test "my project" "my organization" {:api-proj "my project"
                                                                     :api-org  "my organization"})))


(defn perform-create-request-config-test
  [model expected]
  (let [request-config (config/create-request-config model)]
    (is (= request-config expected))))


(deftest create-request-config-test
  (testing "all arguments"
    (perform-create-request-config-test "model-123" {:model "model-123"})))


(defn perform-create-response-config-test
  ([handler-fn expected]
   (perform-create-response-config-test handler-fn nil expected))
  ([handler-fn stream expected]
   (let [response-config (if (nil? stream)
                           (config/create-response-config handler-fn)
                           (config/create-response-config handler-fn stream))]
     (is (= response-config expected)))))


(defn a-handler-fn
  [])

(deftest create-response-config-test
  (testing "handler-fn only"
    (perform-create-response-config-test a-handler-fn {:handler-fn a-handler-fn}))
  (testing "all arguments"
    (perform-create-response-config-test a-handler-fn true {:handler-fn a-handler-fn
                                                            :stream     true})))
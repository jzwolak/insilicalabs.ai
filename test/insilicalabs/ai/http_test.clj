;; Copyright © 2025 Jason Zwolak
;;
;; This source code is licensed under the MIT License.
;; See the LICENSE file in the root directory of this source tree for details.


(ns insilicalabs.ai.http-test
  (:require
    [insilicalabs.ai.http :as http]
    [clj-http.client :as clj-http]
    [clojure.test :refer :all]
    [clojure.string :as str])
  (:import (java.io IOException)))


(def ^:const config-post {:url    "https://example.com"
                          :method :post})

(def ^:const config-url {:url "https://example.com"})


(defn is-every-substring
  [string list]
  (is (every? #(str/includes? (str/lower-case string) (str/lower-case %)) list)
      (str "Expected reason substrings " list " to be in actual reason: " string)))


(defn perform-build-check-config-test
  [config expected]
  (let [build-check-config #'http/build-check-config
        actual (build-check-config config)]
    (is (boolean? (:success actual)) ":success isn't a boolean")
    (if (:success expected)
      (do
        (is (= actual expected) "actual does not equal expected"))
      (do
        (is (false? (:success actual)) ":success isn't 'false'")
        (is-every-substring (:reason actual) (:reason-list expected))
        (let [expected-adjusted (dissoc expected :reason-list)
              actual-adjusted (dissoc actual :reason)]
          (is (= actual-adjusted expected-adjusted) "actual does not equal expected"))))))


(deftest build-check-config-test
  (testing "config is nil"
    (perform-build-check-config-test nil
                                     {:success     false
                                      :error-code  :http-config-nil
                                      :reason-list ["config" "nil"]}))
  (testing "config is not a map"
    (perform-build-check-config-test "config"
                                     {:success     false
                                      :error-code  :http-config-not-map
                                      :reason-list ["config" "must" "map"]}))
  (testing "config is an empty map"
    (perform-build-check-config-test {}
                                     {:success     false
                                      :error-code  :http-config-empty
                                      :reason-list ["config" "map" "empty"]}))
  (testing "config contains an unrecognized key"
    (perform-build-check-config-test {:something "hello"}
                                     {:success     false
                                      :error-code  :http-config-unknown-key
                                      :reason-list ["unknown" "key" "config"]}))
  (testing "':method' key not defined"
    (perform-build-check-config-test {:url "x"}
                                     {:success     false
                                      :error-code  :http-config-method-missing
                                      :reason-list ["config must contain" "key" ":method"]}))
  (testing "':method' key not equal to ':get' or ':post'"
    (perform-build-check-config-test {:method :blah}
                                     {:success     false
                                      :error-code  :http-config-method-invalid
                                      :reason-list ["':method' value" "config" ":get" ":post"]}))
  (testing "':method' set to string 'post'"
    (perform-build-check-config-test {:method "post"}
                                     {:success     false
                                      :error-code  :http-config-method-invalid
                                      :reason-list ["':method' value" "config" ":get" ":post"]}))
  (testing "':url' key not defined"
    (perform-build-check-config-test {:method :get}
                                     {:success     false
                                      :error-code  :http-config-url-missing
                                      :reason-list ["config" "URL" ":url"]}))
  (testing "':url' value not a string"
    (perform-build-check-config-test {:method :get
                                      :url    1}
                                     {:success     false
                                      :error-code  :http-config-url-not-string
                                      :reason-list [":url" "value" "config" "string"]}))
  (testing "success"
    (perform-build-check-config-test {:method :post
                                      :url    "https://example.com"}
                                     {:success true
                                      :config  {:method           :post
                                                :url              "https://example.com"
                                                :throw-exceptions false}})))


(defn perform-request-test
  ([which-fn config expected]
   (perform-request-test which-fn config nil expected nil nil))
  ([which-fn config expected response]
   (perform-request-test which-fn config nil expected response nil))
  ([which-fn config config2 expected response]
   (perform-request-test which-fn config config2 expected response nil))
  ([which-fn config config2 expected response err-msg]
   (with-redefs [clj-http/request (cond
                                    (= response :ioexception) (fn [_] (throw (IOException. ^String err-msg)))
                                    (= response :exception) (fn [_] (throw (Exception. ^String err-msg)))
                                    :else (fn [_] response))
                 clj-http/get (cond
                                (= response :ioexception) (fn [_] (throw (IOException. ^String err-msg)))
                                (= response :exception) (fn [_] (throw (Exception. ^String err-msg)))
                                :else (fn [_] response))
                 clj-http/post (cond
                                 (= response :ioexception) (fn [_] (throw (IOException. ^String err-msg)))
                                 (= response :exception) (fn [_] (throw (Exception. ^String err-msg)))
                                 :else (fn [_] response))]
     (let [actual (cond
                    (= which-fn :request) (if (nil? config2)
                                            (http/request config)
                                            (http/request config config2))
                    (= which-fn :get) (if (nil? config2)
                                        (http/get config)
                                        (http/get config config2))
                    (= which-fn :post) (if (nil? config2)
                                         (http/post config)
                                         (http/post config config2)))]
       (is (boolean? (:success actual)) ":success isn't a boolean")
       (if (:success expected)
         (do
           (is (= actual expected) "actual does not equal expected"))
         (do
           (is (false? (:success actual)) ":success isn't 'false'")
           (is-every-substring (:reason actual) (:reason-list expected))
           (do
             (if (contains? expected :exception-msg)
               (is (= (.getMessage (:exception actual)) (:exception-msg expected)))))
           (let [expected-adjusted (-> expected
                                       (dissoc :exception-msg)
                                       (dissoc :reason-list))
                 actual-adjusted (-> actual
                                     (dissoc :exception)
                                     (dissoc :reason))]
             (is (= actual-adjusted expected-adjusted) "actual does not equal expected"))))))))


(deftest request-test
  ;;
  ;; errors - config
  (testing "config is nil"
    (perform-request-test :request
                          nil
                          {:success     false
                           :error-code  :http-config-nil
                           :reason-list ["config" "nil"]}))
  (testing "config is not a map"
    (perform-request-test :request
                          "config"
                          {:success     false
                           :error-code  :http-config-not-map
                           :reason-list ["config" "must" "map"]}))
  (testing "config is an empty map"
    (perform-request-test :request
                          {}
                          {:success     false
                           :error-code  :http-config-empty
                           :reason-list ["config" "map" "empty"]}))
  (testing "config contains an unrecognized key"
    (perform-request-test :request
                          {:something "hello"}
                          {:success     false
                           :error-code  :http-config-unknown-key
                           :reason-list ["unknown" "key" "config"]}))
  (testing "':method' key not defined"
    (perform-request-test :request
                          {:url "x"}
                          {:success     false
                           :error-code  :http-config-method-missing
                           :reason-list ["config must contain" "key" ":method"]}))
  (testing "':method' key not equal to ':get' or ':post'"
    (perform-request-test :request
                          {:method :blah}
                          {:success     false
                           :error-code  :http-config-method-invalid
                           :reason-list ["':method' value" "config" ":get" ":post"]}))
  (testing "':method' set to string 'post'"
    (perform-request-test :request
                          {:method "post"}
                          {:success     false
                           :error-code  :http-config-method-invalid
                           :reason-list ["':method' value" "config" ":get" ":post"]}))
  (testing "':url' key not defined"
    (perform-request-test :request
                          {:method :get}
                          {:success     false
                           :error-code  :http-config-url-missing
                           :reason-list ["config" "URL" ":url"]}))
  (testing "':url' value not a string"
    (perform-request-test :request
                          {:method :get
                           :url    1}
                          {:success     false
                           :error-code  :http-config-url-not-string
                           :reason-list [":url" "value" "config" "string"]}))
  ;;
  ;; errors - HTTP request/response
  (testing "HTTP error"
    (let [response {:status 400
                    :body   "bad request"}]
      (perform-request-test :request
                            config-post
                            {:success     false
                             :error-code  :http-request-failed
                             :reason-list ["HTTP" "request" "failed"]
                             :response    response}
                            response)))
  ;;
  ;; errors - exception
  (testing "IOException"
    (perform-request-test :request
                          config-post
                          nil
                          {:success       false
                           :error-code    :http-request-failed-ioexception
                           :reason-list   ["HTTP" "request" "failed"]
                           :exception-msg "An IOException occurred."}
                          :ioexception
                          "An IOException occurred."))
  (testing "IOException"
    (perform-request-test :request
                          config-post
                          nil
                          {:success       false
                           :error-code    :http-request-failed-exception
                           :reason-list   ["HTTP" "request" "failed"]
                           :exception-msg "An Exception occurred."}
                          :exception
                          "An Exception occurred."))
  ;;
  ;; success
  (testing "success, 1 config"
    (let [response {:status 200
                    :body   "body"}]
      (perform-request-test :request config-post {:success  true
                                                  :response response} response)))
  (testing "success, 2 configs"
    (let [response {:status 200
                    :body   "body"}]
      (perform-request-test :request config-url {:method :post} {:success  true
                                                                 :response response} response))))


(deftest get-test
  ;;
  ;; errors - config
  (testing "config is nil"
    (perform-request-test :get
                          nil
                          {:success     false
                           ;; not a nil-related error code because config gets merged with a get-related config
                           :error-code  :http-config-url-missing
                           :reason-list ["config" "URL" ":url"]}))
  (testing "config is an empty map"
    (perform-request-test :get
                          {}
                          {:success     false
                           ;; not a nil-related error code because config gets merged with a get-related config
                           :error-code  :http-config-url-missing
                           :reason-list ["config" "URL" ":url"]}))
  (testing "config contains an unrecognized key"
    (perform-request-test :get
                          {:something "hello"}
                          {:success     false
                           :error-code  :http-config-unknown-key
                           :reason-list ["unknown" "key" "config"]}))
  (testing "':url' value not a string"
    (perform-request-test :get
                          {:url 1}
                          {:success     false
                           :error-code  :http-config-url-not-string
                           :reason-list [":url" "value" "config" "string"]}))
  ;;
  ;; errors - HTTP request/response
  (testing "HTTP error"
    (let [response {:status 400
                    :body   "bad request"}]
      (perform-request-test :get
                            config-url
                            {:success     false
                             :error-code  :http-request-failed
                             :reason-list ["HTTP" "request" "failed"]
                             :response    response}
                            response)))
  ;;
  ;; errors - exception
  (testing "IOException"
    (perform-request-test :get
                          config-url
                          nil
                          {:success       false
                           :error-code    :http-request-failed-ioexception
                           :reason-list   ["HTTP" "request" "failed"]
                           :exception-msg "An IOException occurred."}
                          :ioexception
                          "An IOException occurred."))
  (testing "IOException"
    (perform-request-test :get
                          config-url
                          nil
                          {:success       false
                           :error-code    :http-request-failed-exception
                           :reason-list   ["HTTP" "request" "failed"]
                           :exception-msg "An Exception occurred."}
                          :exception
                          "An Exception occurred."))
  ;;
  ;; success
  (testing "success, 1 config"
    (let [response {:status 200
                    :body   "body"}]
      (perform-request-test :get config-url {:success  true
                                             :response response} response)))
  (testing "success, 2 configs"
    (let [response {:status 200
                    :body   "body"}]
      (perform-request-test :get config-url {:url "x"} {:success  true
                                                        :response response} response))))


(deftest post-test
  ;;
  ;; errors - config
  (testing "config is nil"
    (perform-request-test :post
                          nil
                          {:success     false
                           ;; not a nil-related error code because config gets merged with a post-related config
                           :error-code  :http-config-url-missing
                           :reason-list ["config" "URL" ":url"]}))
  (testing "config is an empty map"
    (perform-request-test :post
                          {}
                          {:success     false
                           ;; not a nil-related error code because config gets merged with a post-related config
                           :error-code  :http-config-url-missing
                           :reason-list ["config" "URL" ":url"]}))
  (testing "':url' value not a string"
    (perform-request-test :post
                          {:url 1}
                          {:success     false
                           :error-code  :http-config-url-not-string
                           :reason-list [":url" "value" "config" "string"]}))
  ;;
  ;; errors - HTTP request/response
  (testing "HTTP error"
    (let [response {:status 400
                    :body   "bad request"}]
      (perform-request-test :post
                            config-url
                            {:success     false
                             :error-code  :http-request-failed
                             :reason-list ["HTTP" "request" "failed"]
                             :response    response}
                            response)))
  ;;
  ;; errors - exception
  (testing "IOException"
    (perform-request-test :post
                          config-url
                          nil
                          {:success       false
                           :error-code    :http-request-failed-ioexception
                           :reason-list   ["HTTP" "request" "failed"]
                           :exception-msg "An IOException occurred."}
                          :ioexception
                          "An IOException occurred."))
  (testing "IOException"
    (perform-request-test :post
                          config-url
                          nil
                          {:success       false
                           :error-code    :http-request-failed-exception
                           :reason-list   ["HTTP" "request" "failed"]
                           :exception-msg "An Exception occurred."}
                          :exception
                          "An Exception occurred."))
  ;;
  ;; success
  (testing "success, 1 config"
    (let [response {:status 200
                    :body   "body"}]
      (perform-request-test :post config-url {:success  true
                                              :response response} response)))
  (testing "success, 2 configs"
    (let [response {:status 200
                    :body   "body"}]
      (perform-request-test :post config-url {:url "x"} {:success  true
                                                         :response response} response))))
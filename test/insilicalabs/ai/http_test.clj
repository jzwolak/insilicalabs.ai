(ns insilicalabs.ai.http-test
  (:require
    [insilicalabs.ai.http :as http]
    [clojure.test :refer :all]
    [clojure.string :as str]))


(def config {:url    "https://example.com"
             :method :post})


(defn perform-request-test
  ([config expected]
   (perform-request-test config expected nil))              ;; Call three-argument version with mock-response as nil
  ([config expected mock-response]
   (let [actual-response (http/request config)]
     (is (boolean? (:success actual-response)) ":success isn't a boolean")
     (is (false? (:success actual-response)) ":success isn't 'false'")
     (is (= (:fail-point actual-response) (:fail-point expected)) "Mismatch in :fail-point")
     (is (every? #(str/includes? (str/lower-case (:reason actual-response)) (str/lower-case %)) (:reason expected))
         (str "Expected reason substrings " (:reason expected) " to be in actual reason: " (:reason actual-response))))))


(deftest test-validate-maps
  (testing "config is nil"
    (perform-request-test nil
                          {:success     false
                           :fail-point  :http-config
                           :reason-list ["config" "nil"]}))
  (testing "config is not a map"
    (perform-request-test "config"
                          {:success     false
                           :fail-point  :http-config
                           :reason-list ["config" "must" "map"]}))
  (testing "config is an empty map"
    (perform-request-test {}
                          {:success     false
                           :fail-point  :http-config
                           :reason-list ["config" "map" "empty"]}))
  (testing "':method' key not defined"
    (perform-request-test {:something "x"}
                          {:success     false
                           :fail-point  :http-config
                           :reason-list ["config must contain" "key" ":method"]}))
  (testing "':method' key not equal to ':get' or ':post'"
    (perform-request-test {:method :blah}
                          {:success     false
                           :fail-point  :http-config
                           :reason-list ["':method' value" "config" ":get" ":post"]}))
  (testing "':method' set to string 'post'"
    (perform-request-test {:method "post"}
                          {:success     false
                           :fail-point  :http-config
                           :reason-list ["':method' value" "config" ":get" ":post"]}))
  (testing "':url' key not defined"
    (perform-request-test {:method :get}
                          {:success     false
                           :fail-point  :http-config
                           :reason-list ["config" "URL" ":url"]}))
  (testing "':url' value not a string"
    (perform-request-test {:method :get
                           :url    1}
                          {:success     false
                           :fail-point  :http-config
                           :reason-list [":url" "value" "config" "string"]}))
  ;; todo: finish - mock http/request
  )

; success
; fail-point
; reason
;
; response
; exception
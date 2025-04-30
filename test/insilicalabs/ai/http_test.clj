(ns insilicalabs.ai.http-test
  (:require
    [insilicalabs.ai.http :as http]
    [clojure.test :refer :all]
    [clojure.string :as str]))


(def config {:url    "https://example.com"
             :method :post})


(defn perform-request-test
  ([config expected]
   (perform-request-test config expected nil))
  ([config expected mock-response]
   (let [actual-response (http/request config)]
     (is (boolean? (:success actual-response)) ":success isn't a boolean")
     (is (false? (:success actual-response)) ":success isn't 'false'")
     (is (every? #(str/includes? (str/lower-case (:reason actual-response)) (str/lower-case %)) (:reason expected))
         (str "Expected reason substrings " (:reason expected) " to be in actual reason: " (:reason actual-response))))))


(deftest test-validate-maps
  (testing "config is nil"
    (perform-request-test nil
                          {:success     false
                           :reason-list ["config" "nil"]}))
  (testing "config is not a map"
    (perform-request-test "config"
                          {:success     false
                           :reason-list ["config" "must" "map"]}))
  (testing "config is an empty map"
    (perform-request-test {}
                          {:success     false
                           :reason-list ["config" "map" "empty"]}))
  (testing "':method' key not defined"
    (perform-request-test {:something "x"}
                          {:success     false
                           :reason-list ["config must contain" "key" ":method"]}))
  (testing "':method' key not equal to ':get' or ':post'"
    (perform-request-test {:method :blah}
                          {:success     false
                           :reason-list ["':method' value" "config" ":get" ":post"]}))
  (testing "':method' set to string 'post'"
    (perform-request-test {:method "post"}
                          {:success     false
                           :reason-list ["':method' value" "config" ":get" ":post"]}))
  (testing "':url' key not defined"
    (perform-request-test {:method :get}
                          {:success     false
                           :reason-list ["config" "URL" ":url"]}))
  (testing "':url' value not a string"
    (perform-request-test {:method :get
                           :url    1}
                          {:success     false
                           :reason-list [":url" "value" "config" "string"]}))
  ;; todo: finish - mock http/request
  )

; success
; reason
;
; response
; exception
;; Copyright © 2025 Jason Zwolak
;;
;; This source code is licensed under the MIT License.
;; See the LICENSE file in the root directory of this source tree for details.


(ns insilicalabs.ai.config-test
  (:require
    [clojure.test :refer :all]
    [insilicalabs.ai.config :as config]))


(defn perform-create-http-config-test
  [socket-timeout connection-timeout expected]
  (let [http-config (config/create-http-config socket-timeout connection-timeout)]
    (is (= http-config expected))))


(deftest test-create-http-config
  (testing "all arguments not nil"
    (perform-create-http-config-test 5000 6000 {:socket-timeout     5000
                                                :connection-timeout 6000}))
  (testing "socket-timeout nil"
    (perform-create-http-config-test nil 6000 {:connection-timeout 6000}))
  (testing "connection-timeout nil"
    (perform-create-http-config-test 5000 nil {:socket-timeout 5000}))
  (testing "all arguments nil"
    (perform-create-http-config-test nil nil {})))
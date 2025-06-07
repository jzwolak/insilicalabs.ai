;; Copyright © 2025 Jason Zwolak
;;
;; This source code is licensed under the MIT License.
;; See the LICENSE file in the root directory of this source tree for details.


(ns insilicalabs.ai.examples.providers.openai.chat
  (:require [clojure.java.io :as io]
            [insilicalabs.ai.providers.openai.config :as config]
            [insilicalabs.ai.config :as http-config]
            [insilicalabs.ai.providers.openai.chat :as chat]))

;; USAGE:
;;   In REPL:
;;     (load-file "examples/insilicalabs/ai/examples/providers/openai/chat.clj")
;;   If a file changes, such as:
;;     (require '[insilicalabs.ai.providers.openai.chat] :reload)
;;   To reload all:
;;     (require '[insilicalabs.ai.providers.openai.chat :refer :all] :reload)
;;     (load-file "examples/insilicalabs/ai/examples/providers/openai/chat.clj")


;; QUICK START:
;;   - Write a function to retrieve the API key, such as in 'get-api-key'
;;   - Reference the function 'chat-non-stream-no-handler-fn' for examples in the next steps:
;;   - Create a prepared request, such as with 'create-prepared-request' or 'create-prepared-request-from-config' (see
;;     'demo-config' for the latter)
;;   - Create a vector of previous messages, such as using 'create-messages'.  Often an initial 'system' type message
;;     to prompt the model that it is a "helpful assistant".
;;   - Make the request with '(chat/chat <api key string> <messages string vector> <user message string>)'
;;   - Check if the response was successful with '(:success response)'
;;     - If successful, then print the response content with '(chat/get-response-as-string <response>)'
;;     - If unsuccessful, then print the error as in 'print-error'


(def ^:const model-default "gpt-4o")


(defn get-api-key-path
  "Prompts the user for the file path to the API key and returns that path as a string or 'nil' if the user aborted the
  process."
  []
  (println "Enter the path to the file containing the API key, or enter \"0\" to exit:")
  (let [path (clojure.string/trim (read-line))]
    (cond
      (= path "0") (println "Exiting...")
      (not (.exists (io/file path))) (do
                                       (println "Error: File does not exist. Please enter a valid path.")
                                       (recur))
      :else path)))


(defn get-api-key
  "Reads the file at the string path `api-key-path` and returns the contents."
  [api-key-path]
  (clojure.string/trim (slurp api-key-path)))


(defn print-error
  "Prints the error messages from the response `response` to a request."
  [response]
  (println "An error occurred while processing your request:")
  (println "\n\n")
  (println response)
  (println "\n\n")
  (println "  error-code: " (:error-code response))
  (println "  reason    : " (:reason response))
  (if (contains? response :exception)
    (println "  exception : " (:exception response))))



(defn non-streaming-handler-fn
  [response]
  (if (:success response)
    (do
      (println "")
      (println "HANDLER RESPONSE----------------------------------------------------------------------")
      (println (chat/get-response-as-string response))
      (println "----------------------------------------------------------------------HANDLER RESPONSE"))
    (do
      (println "\n\n")
      (print-error response))))


(defn streaming-handler-fn
  [response]
  (if (:success response)
    (print (chat/get-response-as-string response))
    (do
      (println "\n\n")
      (print-error response))))


(defn complete-non-stream-no-handler-fn
  "Demonstrate a 'complete' request without a handler."
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default})
        messages (chat/create-messages "You are a helpful assistant." nil)]
    (loop []
      (println "Enter text to send for a non-streaming complete without handler function, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving non-streaming complete without handler function")
          :else (let [response (chat/complete prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "")
                  (if (:success response)
                    (println (chat/get-response-as-string response))
                    (print-error response))
                  (recur)))))))


(defn complete-non-stream-with-handler-fn
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default} {:handler-fn non-streaming-handler-fn})
        messages (chat/create-messages "You are a helpful assistant." nil)]
    (loop []
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      ;; A reminder that the messages are not updated for a completion.
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a non-streaming complete with handler function, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving non-streaming complete with handler function")
          :else (let [response (chat/complete prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "")
                  (println "JSON RESPONSE----------------------------------------------------------------------")
                  (println response)
                  (println "----------------------------------------------------------------------JSON RESPONSE")
                  (println "")
                  (recur)))))))


(defn complete-stream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default} {:stream     true
                                                                               :handler-fn streaming-handler-fn})
        messages (chat/create-messages "You are a helpful assistant." nil)]
    (loop []
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      ;; A reminder that the messages are not updated for a completion.
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a streaming complete, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving streaming complete")
          :else (do
                  (println "")
                  (println "RESPONSE----------------------------------------------------------------------")
                  (chat/complete prepared-request (get-api-key api-key-path) messages user-message)
                  (println "")
                  (println "----------------------------------------------------------------------RESPONSE")
                  (recur)))))))


(defn chat-non-stream-no-handler-fn
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default})]
    (loop [messages (chat/create-messages "You are a helpful assistant." nil)]
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      ;; Messages are updated for a chat.
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a non-streaming chat without a handler function, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving non-streaming chat without a handler function")
          :else (let [response (chat/chat prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "")
                  (println "JSON RESPONSE------------------------------------------------------------------")
                  (println response)
                  (println "------------------------------------------------------------------JSON RESPONSE")
                  (println "")
                  (if (:success response)
                    (do
                      (do
                        (println "FORMATTED CONTENT RESPONSE--------------------------------------------------------")
                        (println (chat/get-response-as-string response))
                        (println "--------------------------------------------------------FORMATTED CONTENT RESPONSE"))
                      (recur (:messages response)))
                    (do
                      (print-error response)
                      (recur messages)))))))))


(defn chat-non-stream-with-handler-fn
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default} {:handler-fn non-streaming-handler-fn})]
    (loop [messages (chat/create-messages "You are a helpful assistant." nil)]
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      ;; Messages are updated for a chat.
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a non-streaming chat with handler function, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving non-streaming chat with handler function")
          :else (let [response (chat/chat prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "")
                  (println "RESPONSE----------------------------------------------------------------------")
                  (println response)
                  (println "----------------------------------------------------------------------RESPONSE")
                  (println "")
                  (recur (:messages response))))))))


(defn chat-stream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default} {:stream     true
                                                                               :handler-fn streaming-handler-fn})]
    (loop [messages (chat/create-messages "You are a helpful assistant." nil)]
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      ;; Messages are updated for a chat.
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a streaming chat, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving streaming chat")
          :else (do
                  (println "")
                  (println "RESPONSE----------------------------------------------------------------------")
                  (let [response (chat/chat prepared-request (get-api-key api-key-path) messages user-message)]
                    (println response)
                    (println "----------------------------------------------------------------------RESPONSE")
                    (recur (:messages response)))))))))

(defn demo-config
  [api-key-path]
  (let [config {:http-config     (http-config/create-http-config 6000 10000)
                ;;:auth-config   (config/create-auth-config "my project" "my organization")
                :request-config  (config/create-request-config model-default)
                :response-config (config/create-response-config streaming-handler-fn true)}]
    ;;
    ;; Use the 'config' as a template for future prepared requests, especially when making multiple prepared requests.
    ;; Then some time later...
    ;;
    (let [prepared-request (chat/create-prepared-request-from-config config)]
      (loop [messages (chat/create-messages "You are a helpful assistant." nil)]
        (println "")
        (println "LOOP MESSAGES------------------------------------------------------------------")
        ;; Messages are updated for a chat.
        (println messages)
        (println "------------------------------------------------------------------LOOP MESSAGES")
        (println "")
        (println "PROMPT------------------------------------------------------------------")
        (println "Enter text to send for a streaming chat (demo config), or enter \"0\" to exit.")
        (let [user-message (clojure.string/trim (read-line))]
          (cond
            (= user-message "0") (println "Leaving streaming chat (demo config)")
            :else (do
                    (println "")
                    (println "RESPONSE----------------------------------------------------------------------")
                    (let [response (chat/chat prepared-request (get-api-key api-key-path) messages user-message)]
                      (println response)
                      (println "----------------------------------------------------------------------RESPONSE")
                      (recur (:messages response))))))))))



;; Display a menu of different complete and chat options, get the user's selection, then execute the appropriate
;; function.
(let [api-key-path (get-api-key-path)]
  (loop []
    ;;
    ;; complete
    (println "")
    (println "Select an option:")
    (println "")
    (println "complete:")
    (println "  (1) non-streaming without handler function")
    (println "  (2) non-streaming with handler function")
    (println "  (3) streaming")
    (println "")
    ;;
    ;; chat
    (println "chat:")
    (println "  (4) non-streaming without handler function")
    (println "  (5) non-streaming with handler function")
    (println "  (6) streaming")
    (println "")
    ;;
    ;; create and use config to generate prepared requests
    (println "other:")
    (println "  (7) create config, then streaming")
    (println "")
    ;;
    ;;
    (println "(0) EXIT")
    (let [choice (clojure.string/trim (read-line))]
      (case choice
        ;;
        ;; complete
        "1" (do (complete-non-stream-no-handler-fn api-key-path) (recur))
        "2" (do (complete-non-stream-with-handler-fn api-key-path) (recur))
        "3" (do (complete-stream api-key-path) (recur))
        ;;
        ;; chat
        "4" (do (chat-non-stream-no-handler-fn api-key-path) (recur))
        "5" (do (chat-non-stream-with-handler-fn api-key-path) (recur))
        "6" (do (chat-stream api-key-path) (recur))
        ;;
        ;; demo config
        "7" (do (demo-config api-key-path) (recur))
        ;;
        ;;
        "0" (println "bye")
        (do (println "Error: Invalid choice, please select again.")
            (recur))))))

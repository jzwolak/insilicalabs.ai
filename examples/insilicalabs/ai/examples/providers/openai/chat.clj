(ns insilicalabs.ai.examples.providers.openai.chat
  (:require [clojure.java.io :as io]
            [insilicalabs.ai.config :as config]
            [insilicalabs.ai.providers.openai.chat :as chat]))

;; USAGE:
;;   In REPL:
;;     (load-file "examples/insilicalabs/ai/examples/providers/openai/chat.clj")
;;   If a file changes:
;;     (require '[insilicalabs.ai.providers.openai.chat] :reload)


(def ^:const model-default "gpt-4o")


(defn get-api-key-path
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
  [api-key-path]
  (clojure.string/trim (slurp api-key-path)))


(defn print-error
  [response]
  (println "An error occurred while processing your request:")
  (println "\n\n")
  (println response)
  (println "\n\n")
  (println "  fail-point: " (:fail-point response))
  (println "  reason    : " (:reason response))
  (if (contains? response :exception)
    (println "  exception : " (:exception response))))


(defn streaming-handler-fn
  [response]
  ;;(println "\n\n" response)
  ;;(println (get-in response [:chunk-num]) ":" (chat/get-response-as-string response))
  (print (chat/get-response-as-string response)))


(defn complete-nonstream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default})
        messages (chat/create-messages "You are a helpful assistant." nil)]
    (loop []
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a non-streaming complete, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving non-streaming complete")
          :else (let [response (chat/complete prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "\n\n")
                  (println "RAW RESPONSE------------------------------------------------------------------")
                  (println response)
                  (println "------------------------------------------------------------------RAW RESPONSE")
                  (println "\n\n")
                  (if (:success response)
                    (do
                      (println "FORMATTED RESPONSE--------------------------------------------------------")
                      (println (chat/get-response-as-string response)))
                    (print-error response))
                  (recur)))))))


(defn complete-stream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default} {:stream     true
                                                                               :handler-fn streaming-handler-fn})
        messages (chat/create-messages "You are a helpful assistant." nil)]
    (loop []
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a streaming complete, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving streaming complete")
          :else (do
                  (println "\n\n")
                  (println "RESPONSE----------------------------------------------------------------------")
                  (let [response (chat/complete prepared-request (get-api-key api-key-path) messages user-message)]
                    (println "")
                    (println "----------------------------------------------------------------------RESPONSE")
                    (recur))))))))


(defn chat-nonstream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default})]
    (loop [messages (chat/create-messages "You are a helpful assistant." nil)]
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a non-streaming chat, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving non-streaming chat")
          :else (let [response (chat/chat prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "\n\n")
                  (println "RAW RESPONSE------------------------------------------------------------------")
                  (println response)
                  (println "------------------------------------------------------------------RAW RESPONSE")
                  (println "\n\n")
                  (if (:success response)
                    (do
                      (do
                        (println "FORMATTED RESPONSE--------------------------------------------------------")
                        (println (chat/get-response-as-string response)))
                      (recur (:messages response)))
                    (do
                      (print-error response)
                      (recur messages)))))))))


(defn chat-stream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default} {:stream     true
                                                                               :handler-fn streaming-handler-fn})]
    (loop [messages (chat/create-messages "You are a helpful assistant." nil)]
      (println "")
      (println "LOOP MESSAGES------------------------------------------------------------------")
      (println messages)
      (println "------------------------------------------------------------------LOOP MESSAGES")
      (println "")
      (println "PROMPT------------------------------------------------------------------")
      (println "Enter text to send for a streaming chat, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving streaming chat")
          :else (do
                  (println "\n\n")
                  (println "RESPONSE----------------------------------------------------------------------")
                  (let [response (chat/chat prepared-request (get-api-key api-key-path) messages user-message)]
                    (println "")
                    (println "----------------------------------------------------------------------RESPONSE")
                    (recur (:messages response)))))))))


(let [api-key-path (get-api-key-path)]
  (loop []
    ;;
    ;; complete
    (println "")
    (println "------------------------------------")
    (println "complete:")
    (println "  (1) non-streaming")
    (println "  (2) streaming")
    (println "")
    ;;
    ;; chat
    (println "chat:")
    (println "  (3) non-streaming")
    (println "  (4) streaming")
    (println "")
    ;;
    (println "(0) EXIT")
    (let [choice (clojure.string/trim (read-line))]
      (case choice
        ;;
        ;; complete
        "1" (do (complete-nonstream api-key-path) (recur))
        "2" (do (complete-stream api-key-path) (recur))
        ;;
        ;; chat
        "3" (do (chat-nonstream api-key-path) (recur))
        "4" (do (chat-stream api-key-path) (recur))
        ;;
        "0" (println "bye")
        (do (println "Error: Invalid choice, please select again.")
            (recur))))))

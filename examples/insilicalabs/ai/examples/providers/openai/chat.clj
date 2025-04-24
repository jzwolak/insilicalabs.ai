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
  (print (get-in response [:chunk-num]) (chat/get-response-as-string response)))


(defn complete-nonstream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default})
        messages (chat/create-messages "You are a helpful assistant." nil)]
    (loop []
      (println "")
      (println "Loop messages: " messages)                  ;; todo: debug
      (println "")                                          ;; todo: debug
      (println "------------------------------------")
      (println "Enter text to send for a synchronous completion, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving complete: non-streaming and synchronous (blocking)")
          :else (let [response (chat/complete prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "")
                  (println "\n\n")                          ;; todo: debug
                  (println response)                        ;; todo: debug
                  (println "\n\n")                          ;; todo: debug
                  (if (:success response)
                    (println (chat/get-response-as-string response))
                    (print-error response))
                  (recur)))))))


(defn complete-stream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default} {:stream true
                                                                               :handler-fn streaming-handler-fn})
        messages (chat/create-messages "You are a helpful assistant." nil)]
    (loop []
      (println "")
      (println "Loop messages: " messages)                  ;; todo: debug
      (println "")                                          ;; todo: debug
      (println "------------------------------------")
      (println "Enter text to send for a synchronous completion, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving complete: streaming and synchronous (blocking)")
          :else (let [response (chat/complete prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "")
                  (println "\n\n")                          ;; todo: debug
                  (println response)                        ;; todo: debug
                  (println "\n\n")                          ;; todo: debug
                  (if (:success response)
                    (println (chat/get-response-as-string response))
                    (print-error response))
                  (recur)))))))


(defn chat-nonstream
  [api-key-path]
  (let [prepared-request (chat/create-prepared-request {:model model-default})]
    (loop [messages (chat/create-messages "You are a helpful assistant." nil)]
      (println "")
      (println "Loop messages: " messages)                  ;; todo: debug
      (println "")                                          ;; todo: debug
      (println "------------------------------------")
      (println "Enter text to send for a synchronous completion, or enter \"0\" to exit.")
      (let [user-message (clojure.string/trim (read-line))]
        (cond
          (= user-message "0") (println "Leaving complete: non-streaming and synchronous (blocking)")
          :else (let [response (chat/chat prepared-request (get-api-key api-key-path) messages user-message)]
                  (println "")
                  (println "\n\n")                          ;; todo: debug
                  (println response)                        ;; todo: debug
                  (println "\n\n")                          ;; todo: debug
                  (if (:success response)
                    (do
                      (println (chat/get-response-as-string response))
                      (recur (:messages response)))
                    (do
                      (print-error response)
                      (recur messages)))))))))


(defn chat-stream
  [api-key-path]
  (println "todo"))


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
    (println "  (4) streaming todo")
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

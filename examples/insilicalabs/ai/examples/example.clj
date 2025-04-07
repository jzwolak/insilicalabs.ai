(ns insilicalabs.ai.examples.example
  (:require [clojure.java.io :as io]
            [insilicalabs.ai.providers.openai.auth :as auth]
            [insilicalabs.ai.providers.openai.chat :as chat]))

;; USAGE:
;;   In REPL:
;;     (load-file "examples/insilicalabs/ai/examples/example.clj")


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


(defn complete-synch
  [api-key-path]
  (println "Enter text to send for a synchronous completion, or enter \"0\" to exit.")
  (loop []
    (println "")
    (println "------------------------------------")
    (let [choice (clojure.string/trim (read-line))]
      (cond
        (= choice "0") (println "Leaving 'completion, synchronous'")
        :else (do
                (println ">> " (chat/complete (auth/create-auth-config (get-api-key api-key-path)) choice))
                (println "")
                (recur))))))


(defn bravo
  [api-key-path]
  (println "You selected bravo."))


(defn todo
  [api-key-path]
  (println "This method is not yet implemented."))


(let [api-key-path (get-api-key-path)]
  (loop []
    (println "Select an option:")
    (println "(1) complete, synchronous (blocking), non-stream")
    (println "(2) complete, asynchronous (non-blocking), non-stream")
    (println "todo: streaming?")
    (println "(3) chat, synchronous (blocking), non-stream")
    (println "(4) chat, asynchronous (non-blocking), non-stream")
    (println "todo: streaming?")
    (println "(0) EXIT")
    (let [choice (clojure.string/trim (read-line))]
      (case choice
        "1" (do (complete-synch api-key-path) (recur))
        "2" (do (bravo api-key-path) (recur))
        "3" (do (todo api-key-path) (recur))
        "0" (println "bye")
        (do (println "Error: Invalid choice, please select again.")
            (recur))))))

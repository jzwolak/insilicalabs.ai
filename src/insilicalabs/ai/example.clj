(ns insilicalabs.ai.example
  (:require [clojure.java.io :as io]
            [insilicalabs.ai.providers.openai.auth :as auth]
            [insilicalabs.ai.providers.openai.chat :as chat]))

;; WARNING: this is a temporary file for experimentation and will not be kept
;;
;; USAGE:
;;   (require '[insilicalabs.ai.example :as example] :reload)


(defn get-api-key
  []
  (println "Enter the path to the file containing the API key, or enter \"0\" to exit:")
  (let [path (clojure.string/trim (read-line))]
    (cond
      (= path "0") (println "Exiting...")
      (not (.exists (io/file path))) (do
                                       (println "Error: File does not exist. Please enter a valid path.")
                                       (recur))
      :else (let [api-key (clojure.string/trim (slurp path))]
              api-key))))


(defn complete-synch
  [auth-config]
  (println "Enter text to send for a synchronous completion, or enter \"0\" to exit.")
  (loop []
    (println "")
    (println "------------------------------------")
    (let [choice (clojure.string/trim (read-line))]
      (cond
        (= choice "0") (println "Leaving 'completion, synchronous'")
        :else (do
                (println ">> " (chat/complete auth-config choice))
                (println "")
                (recur))))))


(defn bravo
  [auth-config]
  (println "You selected bravo."))


(defn charlie
  [auth-config]
  (println "You selected charlie."))


(let [api-key (get-api-key)]
  (if (some? api-key)
    (let [auth-config (auth/create-auth-config api-key)]
      (loop []
        (println "Select an option:")
        (println "(1) complete, synchronous")
        (println "(2) bravo")
        (println "(3) charlie")
        (println "(0) EXIT")
        (let [choice (clojure.string/trim (read-line))]
          (case choice
            "1" (do (complete-synch auth-config) (recur))
            "2" (do (bravo auth-config) (recur))
            "3" (do (charlie auth-config) (recur))
            "0" (println "bye")
            (do (println "Error: Invalid choice, please select again.")
                (recur))))))))

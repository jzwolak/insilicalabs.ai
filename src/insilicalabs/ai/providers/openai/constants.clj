(ns insilicalabs.ai.providers.openai.constants)


(def ^:const request-failed-limit-keyword :request-failed-limit)
(def ^:const request-failed-limit-message "Response stopped due to token limit being reached.")

(def ^:const request-failed-content-filter-keyword :request-failed-content-filter)
(def ^:const request-failed-content-filter-message "The response was blocked by the content filter for potentially sensitive or unsafe content.")
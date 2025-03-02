(ns insilicalabs.ai.providers.openai.http
  (:require [insilicalabs.ai.http :as http]))


(def ^:const http-post-config-mods {:content-type :json})


(defn ^:impure post
  [& configs]
  (http/post (apply merge http-post-config-mods configs)))


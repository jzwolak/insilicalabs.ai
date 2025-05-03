(ns insilicalabs.ai.config)


(defn create-http-config
  [socket-timeout connection-timeout]
  "Creates an HTTP configuration to set the socket timeout to `socket-timeout` and the connection timeout to
  `connection-timeout`; both times are in milliseconds.  If an argument is 'nil', then that entry is omitted from the
  configuration.

  The connection timeout sets the time after which, when no answer is received from the remote machine, that a timeout
  is declared.

  The socket timeout sets the time after which, when no data is received between the last received data and current,
  that a timeout is declared."
  (cond-> {}
          (some? socket-timeout) (assoc :socket-timeout socket-timeout)
          (some? connection-timeout) (assoc :connection-timeout connection-timeout)))

#clojure -J-Dclojure.server.repl="{:port 5555 :accept clojure.core.server/repl}" -J-Dconfig=./config.clj -X cybernut.core/main

#clojure -J-Dconfig=./config.clj -X cybernut.core/main

#clojure -M:nREPL -m nrepl.cmdline
clojure -M:nREPL

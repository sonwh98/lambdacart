# deps.edn scripts
```bash

#run nREPL
% clj -M:nREPL

#format code
% clj -M:fmt

#recompiles cljs to js on change
% 
% clj -M:dev -m shadow.cljs.devtools.cli watch grid
% clj -M:dev -m shadow.cljs.devtools.cli watch grid-remote

% clj -M:dev -m shadow.cljs.devtools.cli watch main
% clj -M:dev -m shadow.cljs.devtools.cli watch main-remote

#compile cljs
% clj -M:cljs -m shadow.cljs.devtools.cli compile grid-prod
% clj -M:cljs -m shadow.cljs.devtools.cli compile main-prod

# starts CHP and websocket server
% clj -M:server

## start datomic
% ./bin/transactor config/postgres-transactor.properties

## start cljs repl
(shadow/repl :grid-remote)

```

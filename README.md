# run in Docker
```bash
% docker build -t lambdacart .
% ./scripts/start-docker.sh
```


then open browser http://localhost:3001/grid.chp

# copy files to container without redeploying
```bash
% docker cp public/. <container-name>:/app/public 
% docker cp public/. mai:/app/public
```

# start bash shell in docker
```bash
% docker exec -it lambdacart /bin/sh 
```

# deps.edn scripts
```bash

#run nREPL
% clj -M:nREPL

#format code
% clj -M:fmt

#recompiles cljs to js on change
% clj -M:cljs -m shadow.cljs.devtools.cli watch grid

#compile cljs
% clj -M:cljs -m shadow.cljs.devtools.cli compile app

# starts CHP and websocket server
% clj -M:server

```

# run the start script

```bash
% npx shadow-cljs release lambdacart
% ./scripts/start.sh
```

# run in Docker
```bash
% docker build -t lambdacart .
% ./scripts/start-docker.sh
```


then open browser http://localhost:3001

#copy files to container without redeploying
```bash
% docker cp public/. <container-name>:/app/public 
% docker cp public/. mai:/app/public
```

#start bash shell in docker
```bash
% docker exec -it lambdakids /bin/sh 
```

#deps.edn scripts
```bash

#run nREPL
% clj -M:nREPL

#format code
% clj -M:fmt

k#recompiles cljs to js on change
% clj -M:cljs watch app

#recompiles cljs to js on change
% clj -M:cljs -m shadow.cljs.devtools.cli watch app

#compile cljs
% clj -M:cljs -m shadow.cljs.devtools.cli compile app

# starts chp web-server
% clj -M:chp 
```

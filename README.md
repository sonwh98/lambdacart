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

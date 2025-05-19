server{
    listen       80;
    server_name  mai.bumble.fish
    return	 301  https://$host$request_uri;
}

server {
    listen 	 443 ssl;
    server_name  mai.bumble.fish;
    client_max_body_size 20M;

    gzip            on;
    gzip_min_length 1000;
    gzip_proxied    any;
    gzip_comp_level 9;
    gzip_types text/plain text/css application/json application/javascript application/x-javascript text/javascript text/xml application/xml application/rss+xml application/atom+xml application/rdf+xml;
      
    
    location / {
    	    proxy_set_header X-Real-IP  $remote_addr;
            proxy_set_header X-Forwarded-For $remote_addr;
            proxy_set_header Host $host;
            proxy_pass http://localhost:3001;
    }


}

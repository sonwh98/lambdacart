server{
    if ($host = www.ttgamestock.com) {
        return 301 https://$host$request_uri;
    } # managed by Certbot


    listen       80;
    server_name  www.ttgamestock.com;	
    return	 301  https://$host$request_uri;


}

server {
    listen 	 443 ssl;
    server_name  www.ttgamestock.com;
    client_max_body_size 20M;

    error_log /var/log/nginx/debug.log debug;
    
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

    location /wsstream {
      proxy_pass http://localhost:3001;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "upgrade";
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    location /shadow-cljs/  {
      proxy_pass http://localhost:9630/;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "upgrade";
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }



    ssl_certificate /etc/letsencrypt/live/www.ttgamestock.com/fullchain.pem; # managed by Certbot
    ssl_certificate_key /etc/letsencrypt/live/www.ttgamestock.com/privkey.pem; # managed by Certbot
}

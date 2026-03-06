# LambdaCart Quickstart Guide

This guide describes how to set up the LambdaCart system on a new Linux machine using Docker.

## Prerequisites

- **Docker & Docker Compose v2+**
- **Nginx** (for reverse proxy and SSL)
- **Datomic Pro** zip file (e.g., `datomic-pro-1.0.7491.zip`)
- **Java 21** (if running migration scripts from host)

## Initial Setup

1. **Clone the project**:
   ```bash
   git clone <repository-url> ~/workspace/lambdacart
   cd ~/workspace/lambdacart
   ```

2. **Prepare Datomic Pro**:
   Place the extracted Datomic Pro directory in the project root as `datomic-pro`:
   ```bash
   # If you have the zip
   unzip datomic-pro-1.0.7491.zip -d ~/app/
   ln -s ~/app/datomic-pro-1.0.7491 ~/workspace/lambdacart/datomic-pro
   ```

3. **Configure Environment**:
   Ensure `env.sh` has the correct credentials and `DATOMIC_HOME` points to your local Datomic installation.

## Docker Deployment

1. **Build the images**:
   ```bash
   docker compose -p lambdacart build
   ```

2. **Start Database and Transactor**:
   ```bash
   docker compose -p lambdacart up -d db transactor
   ```

3. **Initialize Postgres Schema**:
   Datomic requires a specific table in Postgres when using the SQL protocol.
   ```bash
   printf "CREATE TABLE datomic_kvs (id text NOT NULL, rev integer, map text, val bytea, CONSTRAINT pk_id PRIMARY KEY (id)); ALTER TABLE datomic_kvs OWNER TO datomic;" | docker exec -i lambdacart-db-1 psql -U datomic -d datomic
   ```

4. **Restore Database Data** (if migrating):
   Use the provided restore script to migrate data to the Docker Postgres instance (port 5433).
   ```bash
   source env.sh
   ./restore-docker.sh <your-backup-file.tar.gz>
   ```

5. **Start the Application**:
   ```bash
   docker compose -p lambdacart up -d app
   ```

## Nginx Configuration

Update your Nginx site configuration (e.g., `/etc/nginx/sites-available/www.ttgamestock.com`) to proxy traffic to port **3003**:

```nginx
location / {
    proxy_pass http://localhost:3003;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}

location /wsstream {
    proxy_pass http://localhost:3003;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
}
```

Reload Nginx:
```bash
sudo nginx -t && sudo systemctl reload nginx
```

## Troubleshooting

- **Check Logs**: `docker compose -p lambdacart logs -f app`
- **Database Connectivity**: Verify the transactor logs if the app shows `ActiveMQNotConnectedException`.
- **Encryption**: If you see security exceptions, ensure `-Ddatomic.encryptChannel=false` is set in the Dockerfile CMD.

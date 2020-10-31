# FAQ

## Where is my data stored?
In local development, Pragma creates a local PostgreSQL instance and stores the data there. You can overwrite the location of the Postgres database by setting the `DAEMON_PG_HOST`, `DAEMON_PG_PORT`, `DAEMON_PG_DB_NAME`, `DAEMON_PG_USER`, and `DAEMON_PG_PASSWORD` environment variables passed to the Pragma daemon by modifying the `docker-compose.yml` file used to run the daemon.

## How can I hook Pragma to analytics tools like Prometheus?
Pragma is built on top of Apache OpenWhisk, which already integrates well with many analytics/monitoring tools. OpenWhisk can also be deployed to Kubernetes, so you have access to its amazing tooling ecosystem.

## Can I access data from multiple databases through Pragma?
Pragma does not currently support distributing data across multiple databases, but it's entirely possible to implement the ability to specify the location of the data for each model (or even field.) We'd love to hear your suggestions about this kind of future work, so keep in touch!

## Does Pragma support GraphQL subscriptions?
No. It's very high on the priority list though.

## Can I integrate Pragma with my pub/sub system?
Pragma already uses OpenWhisk, so it wouldn't be too difficult to have it publish events through Kafka. Tell us if you need this so badly and we'll move it up the priority list!
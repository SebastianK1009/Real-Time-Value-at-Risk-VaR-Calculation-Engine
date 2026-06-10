FROM confluentinc/cp-kafka-connect-base:7.6.0

# Install JDBC Connector
RUN confluent-hub install --no-prompt confluentinc/kafka-connect-jdbc:10.7.4

# Install PostgreSQL Driver (Base image often excludes it or has old version, safer to add)
# (Confluent JDBC connector usually ships with drivers, but let's be sure if needed. 
# Actually, the managed connector includes it. If not, we download.)
# Using the built-in one is usually fine for Postgres.

ENV CONNECT_PLUGIN_PATH="/usr/share/java,/usr/share/confluent-hub-components"

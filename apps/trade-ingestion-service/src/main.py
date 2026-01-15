import os
import time
import json
import requests
from kafka import KafkaProducer

# Configuration
KAFKA_BOOTSTRAP_SERVERS = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'kafka:9092')
KAFKA_TOPIC = os.getenv('KAFKA_TOPIC', 'trades.raw.events')
OMS_URL = os.getenv('OMS_URL', 'http://trade-data-simulator:5000/trade-feed')

def create_kafka_producer():
    attempt = 0
    while True:
        try:
            producer = KafkaProducer(
                bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS,
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                key_serializer=lambda k: k.encode('utf-8') if k else None
            )
            print(f"Connected to Kafka at {KAFKA_BOOTSTRAP_SERVERS}")
            return producer
        except Exception as e:
            attempt += 1
            print(f"Failed to connect to Kafka (attempt {attempt}): {e}")
            time.sleep(5)

def connect_to_oms_stream(producer):
    print(f"Connecting to OMS Stream at {OMS_URL}...")
    try:
        # stream=True keeps the connection open
        with requests.get(OMS_URL, stream=True, timeout=None) as response:
            if response.status_code != 200:
                print(f"Failed to connect to OMS: Status {response.status_code}")
                return

            for line in response.iter_lines():
                if line:
                    decoded_line = line.decode('utf-8')
                    if decoded_line.startswith('data: '):
                        # Extract JSON part
                        json_str = decoded_line.replace('data: ', '', 1)
                        try:
                            trade = json.loads(json_str)
                            
                            # Key by portfolio_id for aggregation
                            key = trade.get('portfolio_id')
                            producer.send(KAFKA_TOPIC, key=key, value=trade)
                            
                            print(f"Ingested Trade: {trade['instrument']} {trade['direction']} for {key}")
                        except json.JSONDecodeError:
                            print(f"Failed to parse JSON: {json_str}")
                            
    except requests.exceptions.RequestException as e:
        print(f"Connection to OMS lost: {e}")

def main():
    print("Starting Trade Ingestion Service (Connector Mode)...")
    producer = create_kafka_producer()

    while True:
        try:
            connect_to_oms_stream(producer)
        except Exception as e:
            print(f"Unexpected error: {e}")
        
        print("Retrying connection to OMS in 5 seconds...")
        time.sleep(5)

if __name__ == "__main__":
    main()


import time
import json
import random
import uuid
import threading
import queue
from datetime import datetime, timezone
from flask import Flask, Response, stream_with_context
from faker import Faker

app = Flask(__name__)
fake = Faker()

# Configuration
PORTFOLIOS = ['portfolio-A', 'portfolio-B', 'desk-alpha', 'desk-beta']
INSTRUMENTS = ['AAPL', 'EUR/USD', 'SPY', 'BTC/USD', 'MSFT', 'META', 'QQQ', 'GBP/USD']
DIRECTIONS = ['BUY', 'SELL']

# Global queue to broadcast trades to connected clients
# In a real app, this might be a Redis channel or DB tail
msg_queue = queue.Queue(maxsize=1000)

def generate_trades_background():
    """Background thread that acts as the 'Matching Engine' creating trades"""
    print("Starting Matching Engine Simulation...")
    while True:
        try:
            trade = {
                'trade_id': str(uuid.uuid4()),
                'portfolio_id': random.choice(PORTFOLIOS),
                'instrument': random.choice(INSTRUMENTS),
                'quantity': random.randint(1, 100) * 10,
                'direction': random.choice(DIRECTIONS),
                'timestamp': datetime.now(timezone.utc).isoformat()
            }
            
            # Put trade in queue
            if not msg_queue.full():
                msg_queue.put(trade)
            
            # Random interval between trades
            time.sleep(random.uniform(0.5, 2.0))
            
        except Exception as e:
            print(f"Error generating trade: {e}")
            time.sleep(1)

# Start background generator
generator_thread = threading.Thread(target=generate_trades_background, daemon=True)
generator_thread.start()

@app.route('/health')
def health():
    return {'status': 'healthy', 'service': 'trade-data-simulator'}

@app.route('/trade-feed')
def trade_feed():
    """Server-Sent Events (SSE) endpoint causing a continuous stream of trades"""
    def generate():
        while True:
            # Get trade from queue, blocking if necessary
            # We copy it because queue.get removes it, but for multiple clients 
            # in this simple memory queue, only one would get it.
            # To support multiple clients properly in memory we'd need a PubSub pattern.
            # For simplicity: We will just generate a new one specifically for this stream 
            # OR we can just generate trades *inside* this generator slightly randomized.
            
            # Approach B: Generate on the fly for the connected client to ensure 
            # every client gets a flow, rather than competing for the single queue.
            
            trade = {
                'trade_id': str(uuid.uuid4()),
                'portfolio_id': random.choice(PORTFOLIOS),
                'instrument': random.choice(INSTRUMENTS),
                'quantity': random.randint(1, 100) * 10,
                'direction': random.choice(DIRECTIONS),
                'timestamp': datetime.now(timezone.utc).isoformat()
            }
            
            # SSE format: "data: <json>\n\n"
            yield f"data: {json.dumps(trade)}\n\n"
            
            time.sleep(random.uniform(1.0, 3.0))

    return Response(stream_with_context(generate()), mimetype='text/event-stream')

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)

#!/usr/bin/env python3
"""
TCP Socket Market Data Simulator
Simulates real-time market data for multiple financial instruments
and streams them to connected clients via TCP sockets.
"""

import socket
import threading
import time
import json
import logging
import random
import math
from datetime import datetime, timezone
from typing import Dict, List, Set
import signal
import sys

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class MarketDataGenerator:
    """Generates realistic market data for financial instruments"""
    
    def __init__(self, symbol: str, initial_price: float, volatility: float = 0.02):
        self.symbol = symbol
        self.current_price = initial_price
        self.volatility = volatility
        self.bid_ask_spread = initial_price * 0.001  # 0.1% spread
        self.last_update = time.time()
        
    def generate_tick(self) -> Dict:
        """Generate a single market data tick using Geometric Brownian Motion"""
        # Time delta since last update
        dt = time.time() - self.last_update
        self.last_update = time.time()
        
        # Geometric Brownian Motion for price simulation
        drift = 0.0001  # Small upward drift
        shock = random.gauss(0, 1)
        price_change = self.current_price * (drift * dt + self.volatility * shock * math.sqrt(dt))
        
        # Update current price
        self.current_price += price_change
        
        # Ensure price stays positive
        self.current_price = max(self.current_price, 0.01)
        
        # Calculate bid/ask prices
        bid_price = self.current_price - (self.bid_ask_spread / 2)
        ask_price = self.current_price + (self.bid_ask_spread / 2)
        
        # Generate volumes
        bid_volume = random.randint(100, 10000)
        ask_volume = random.randint(100, 10000)
        
        # Create market data tick
        tick = {
            'symbol': self.symbol,
            'timestamp': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
            'bid': round(bid_price, 2),
            'ask': round(ask_price, 2),
            'last': round(self.current_price, 2),
            'bid_volume': bid_volume,
            'ask_volume': ask_volume,
            'high': round(self.current_price * 1.05, 2),
            'low': round(self.current_price * 0.95, 2),
            'volume': random.randint(1000, 100000)
        }
        
        return tick


class TCPMarketDataSimulator:
    """TCP Socket Server that streams market data to connected clients"""
    
    def __init__(self, host: str = '0.0.0.0', port: int = 9999):
        self.host = host
        self.port = port
        self.server_socket = None
        self.clients: Set[socket.socket] = set()
        self.clients_lock = threading.Lock()
        self.running = False
        
        # Initialize market data generators for various instruments
        self.instruments = {
            # US Tech Stocks
            'AAPL': MarketDataGenerator('AAPL', 150.0, 0.025),
            'GOOGL': MarketDataGenerator('GOOGL', 2800.0, 0.03),
            'MSFT': MarketDataGenerator('MSFT', 300.0, 0.022),
            'TSLA': MarketDataGenerator('TSLA', 700.0, 0.05),
            'AMZN': MarketDataGenerator('AMZN', 3300.0, 0.028),
            'NVDA': MarketDataGenerator('NVDA', 500.0, 0.045),
            'META': MarketDataGenerator('META', 350.0, 0.035),
            
            # Indices
            'SPY': MarketDataGenerator('SPY', 450.0, 0.015),
            'QQQ': MarketDataGenerator('QQQ', 380.0, 0.018),
            
            # Forex
            'EUR/USD': MarketDataGenerator('EUR/USD', 1.18, 0.008),
            'GBP/USD': MarketDataGenerator('GBP/USD', 1.38, 0.01),
            'USD/JPY': MarketDataGenerator('USD/JPY', 110.0, 0.012),
            
            # Crypto
            'BTC/USD': MarketDataGenerator('BTC/USD', 45000.0, 0.08),
            'ETH/USD': MarketDataGenerator('ETH/USD', 3000.0, 0.1),
            'SOL/USD': MarketDataGenerator('SOL/USD', 150.0, 0.12)
        }
        
        # Market data update interval (seconds)
        self.update_interval = 0.1  # 100ms = 10 ticks per second
        
    def start(self):
        """Start the TCP server and market data streaming"""
        self.running = True
        
        # Create server socket
        self.server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.server_socket.bind((self.host, self.port))
        self.server_socket.listen(5)
        
        logger.info(f"Market Data Simulator started on {self.host}:{self.port}")
        logger.info(f"Streaming data for instruments: {', '.join(self.instruments.keys())}")
        
        # Start client acceptance thread
        accept_thread = threading.Thread(target=self._accept_clients, daemon=True)
        accept_thread.start()
        
        # Start market data generation thread
        data_thread = threading.Thread(target=self._generate_market_data, daemon=True)
        data_thread.start()
        
        # Keep main thread alive
        try:
            while self.running:
                time.sleep(1)
        except KeyboardInterrupt:
            logger.info("Received shutdown signal")
            self.stop()
    
    def _accept_clients(self):
        """Accept incoming client connections"""
        while self.running:
            try:
                self.server_socket.settimeout(1.0)
                client_socket, address = self.server_socket.accept()
                
                with self.clients_lock:
                    self.clients.add(client_socket)
                
                logger.info(f"Client connected from {address}. Total clients: {len(self.clients)}")
                
                # Send welcome message
                welcome_msg = {
                    'type': 'welcome',
                    'message': 'Connected to Market Data Simulator',
                    'instruments': list(self.instruments.keys()),
                    'update_interval_ms': self.update_interval * 1000
                }
                try:
                    self._send_to_client(client_socket, welcome_msg)
                except Exception:
                    # Client disconnected immediately (e.g., health check)
                    with self.clients_lock:
                        if client_socket in self.clients:
                            self.clients.remove(client_socket)
                    try:
                        client_socket.close()
                    except:
                        pass
                
            except socket.timeout:
                continue
            except Exception as e:
                if self.running:
                    logger.error(f"Error accepting client: {e}")
    
    def _generate_market_data(self):
        """Generate and broadcast market data to all connected clients"""
        while self.running:
            try:
                # Generate ticks for all instruments
                market_data = []
                for instrument in self.instruments.values():
                    tick = instrument.generate_tick()
                    market_data.append(tick)
                
                # Create market data message
                message = {
                    'type': 'market_data',
                    'timestamp': datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z'),
                    'data': market_data
                }
                
                # Broadcast to all clients
                self._broadcast(message)
                
                # Sleep for update interval
                time.sleep(self.update_interval)
                
            except Exception as e:
                logger.error(f"Error generating market data: {e}")
    
    def _broadcast(self, message: Dict):
        """Broadcast message to all connected clients"""
        disconnected_clients = []
        
        with self.clients_lock:
            for client in self.clients:
                try:
                    self._send_to_client(client, message)
                except (BrokenPipeError, ConnectionResetError, OSError):
                    # Client disconnected - silently add to removal list
                    disconnected_clients.append(client)
                except Exception as e:
                    logger.warning(f"Failed to send to client: {e}")
                    disconnected_clients.append(client)
            
            # Remove disconnected clients
            for client in disconnected_clients:
                self.clients.remove(client)
                try:
                    client.close()
                except:
                    pass
                if disconnected_clients:
                    logger.info(f"Client disconnected. Total clients: {len(self.clients)}")
    
    def _send_to_client(self, client_socket: socket.socket, message: Dict):
        """Send JSON message to a specific client"""
        json_data = json.dumps(message) + '\n'
        client_socket.sendall(json_data.encode('utf-8'))
    
    def stop(self):
        """Stop the server and close all connections"""
        logger.info("Shutting down Market Data Simulator...")
        self.running = False
        
        # Close all client connections
        with self.clients_lock:
            for client in self.clients:
                try:
                    client.close()
                except:
                    pass
            self.clients.clear()
        
        # Close server socket
        if self.server_socket:
            try:
                self.server_socket.close()
            except:
                pass
        
        logger.info("Market Data Simulator stopped")


def signal_handler(sig, frame):
    """Handle shutdown signals gracefully"""
    logger.info("Interrupt received, shutting down...")
    sys.exit(0)


def main():
    """Main entry point"""
    # Setup signal handlers
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # Get configuration from environment or use defaults
    import os
    host = os.getenv('MARKET_DATA_HOST', '0.0.0.0')
    port = int(os.getenv('MARKET_DATA_PORT', '9999'))
    
    # Create and start simulator
    simulator = TCPMarketDataSimulator(host=host, port=port)
    
    try:
        simulator.start()
    except Exception as e:
        logger.error(f"Fatal error: {e}")
        sys.exit(1)


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""
Simple test client for the TCP Market Data Simulator
"""

import socket
import json
import sys

def test_market_data_simulator(host='localhost', port=9999, duration=5):
    """Connect to the market data simulator and print received data"""
    
    print(f"Connecting to Market Data Simulator at {host}:{port}...")
    
    try:
        # Create socket and connect
        client_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client_socket.connect((host, port))
        client_socket.settimeout(duration)
        
        print("✓ Connected successfully!\n")
        
        # Create a file-like buffer to read line by line
        socket_file = client_socket.makefile('r')
        
        message_count = 0
        
        # Read messages
        while True:
            try:
                line = socket_file.readline()
                if not line:
                    break
                    
                # Parse JSON message
                message = json.loads(line.strip())
                message_count += 1
                
                # Print message details
                if message['type'] == 'welcome':
                    print("=" * 70)
                    print("WELCOME MESSAGE:")
                    print(f"  Message: {message['message']}")
                    print(f"  Instruments: {', '.join(message['instruments'])}")
                    print(f"  Update Interval: {message['update_interval_ms']} ms")
                    print("=" * 70 + "\n")
                    
                elif message['type'] == 'market_data':
                    print(f"Market Data Update #{message_count - 1}")
                    print(f"  Timestamp: {message['timestamp']}")
                    print(f"  Instruments: {len(message['data'])}")
                    
                    # Show first 3 instruments as sample
                    print("\n  Sample Data:")
                    for tick in message['data'][:3]:
                        print(f"    {tick['symbol']:>10} | Bid: ${tick['bid']:>10,.2f} | Ask: ${tick['ask']:>10,.2f} | Last: ${tick['last']:>10,.2f}")
                    
                    print("-" * 70 + "\n")
                    
            except socket.timeout:
                print(f"\n✓ Test completed after {duration} seconds")
                print(f"✓ Received {message_count} messages")
                break
            except json.JSONDecodeError as e:
                print(f"✗ Failed to parse JSON: {e}")
                continue
                
    except ConnectionRefusedError:
        print("✗ Connection refused. Is the simulator running?")
        return False
    except Exception as e:
        print(f"✗ Error: {e}")
        return False
    finally:
        try:
            client_socket.close()
            print("✓ Connection closed")
        except:
            pass
    
    return True

if __name__ == '__main__':
    # Test the simulator for 5 seconds
    success = test_market_data_simulator(duration=5)
    sys.exit(0 if success else 1)

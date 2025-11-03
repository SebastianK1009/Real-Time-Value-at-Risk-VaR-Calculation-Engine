# Multi-stage build for Market Data Simulator
# Stage 1: Build stage
FROM python:3.12-slim as builder

# Set working directory
WORKDIR /app

# Install build dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements if you have any (create one if needed)
# COPY requirements.txt .
# RUN pip install --user --no-cache-dir -r requirements.txt

# Stage 2: Runtime stage
FROM python:3.12-slim

# Set working directory
WORKDIR /app

# Create non-root user for security
RUN groupadd -r appuser && \
    useradd -r -g appuser -u 1001 appuser && \
    chown -R appuser:appuser /app

# Copy Python packages from builder (if you used requirements.txt)
# COPY --from=builder /root/.local /home/appuser/.local

# Copy application code
COPY --chown=appuser:appuser src/market-data-simulator.py .

# Set environment variables
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    MARKET_DATA_HOST=0.0.0.0 \
    MARKET_DATA_PORT=9999

# Expose the port
EXPOSE 9999

# Switch to non-root user
USER appuser

# Health check - reads welcome message before closing to avoid broken pipe
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD python -c "import socket; s = socket.socket(socket.AF_INET, socket.SOCK_STREAM); s.settimeout(5); s.connect(('localhost', 9999)); s.recv(1024); s.close()" || exit 1

# Run the application
CMD ["python", "market-data-simulator.py"]

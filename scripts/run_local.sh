#!/usr/bin/env bash
set -e

echo "==> Starting DevScope locally"

echo "--> Starting PostgreSQL..."
docker-compose up -d
echo "--> Waiting for PostgreSQL to be ready..."
until docker-compose exec -T postgres pg_isready -U devscope -d devscope; do
  sleep 1
done

echo "--> Installing Python dependencies for chunker..."
pip3 install -r scripts/requirements.txt --quiet

echo "--> Starting Spring Boot backend..."
cd backend && ./gradlew bootRun

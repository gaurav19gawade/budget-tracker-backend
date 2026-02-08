#!/bin/bash

echo "🔍 Checking PostgreSQL container..."
docker ps | grep budget-tracker-db

echo ""
echo "📊 Checking tables..."
docker exec -it budget-tracker-db psql -U postgres -d budget_tracker -c "\dt"

echo ""
echo "👤 Checking users table structure..."
docker exec -it budget-tracker-db psql -U postgres -d budget_tracker -c "\d users"

echo ""
echo "✅ Validation complete!"
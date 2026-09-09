#!/usr/bin/env bash
#
# AND/OR filter-tree examples for the beanquery demo, for people without IntelliJ.
# Same cases (b-e) as the "Filtry AND/OR (drzewo)" section of demo.http.
#
# Usage:
#   mvn -pl beanquery-demo spring-boot:run      # in another terminal
#   ./beanquery-demo/demo.curl.sh
#
# Requires: curl, jq

set -euo pipefail

BASE="${BASE:-http://localhost:8080/api/bq}"

query() {
  curl -sS -X POST "$BASE/product/query" \
    -H 'Content-Type: application/json' \
    -d "$1"
}

hr() { printf '\n%s\n' "----------------------------------------------------------------------"; }

# --- b) simple OR --------------------------------------------------------------
hr
echo "b) Proste OR: price < 50 OR price > 500"
echo "   Jako AND te warunki wykluczają się (0 wierszy); OR to nadzbiór."
echo "   Oczekiwane: ~29 wierszy."
query '{
  "select": ["id", "name", "price"],
  "filters": { "logic": "or", "children": [
    { "field": "price", "op": "LT", "value": 50 },
    { "field": "price", "op": "GT", "value": 500 }
  ] },
  "sort": [{ "field": "price", "direction": "ASC" }],
  "page": { "number": 0, "size": 100 }
}' | jq '{total: .page.totalElements, first3: [.rows[0:3][] | {id, name, price}]}'

# --- c) AND with a nested OR -------------------------------------------------
hr
echo "c) AND z zagnieżdżonym OR:"
echo "   category.name = 'Elektronika' AND (name ILIKE 'pro' OR price > 500)"
echo "   Oczekiwane: ~5 wierszy (id 1, 2, 4, 5, 10). Bez OR byłyby 3."
query '{
  "select": ["id", "name", "price", "category.name"],
  "filters": { "logic": "and", "children": [
    { "field": "category.name", "op": "EQ", "value": "Elektronika" },
    { "logic": "or", "children": [
      { "field": "name", "op": "ILIKE", "value": "pro" },
      { "field": "price", "op": "GT", "value": 500 }
    ] }
  ] },
  "sort": [{ "field": "price", "direction": "ASC" }],
  "page": { "number": 0, "size": 20 }
}' | jq '{total: .page.totalElements, rows: [.rows[] | {id, name, price}]}'

# --- d) (A OR B) AND (C OR D) ----------------------------------------------
hr
echo "d) (A OR B) AND (C OR D) - dwa OR-y spięte AND-em:"
echo "   (category.name = 'Elektronika' OR category.name = 'Audio')"
echo "     AND (price < 100 OR name ILIKE 'pro')"
echo "   Oczekiwane: ~9 wierszy."
query '{
  "select": ["id", "name", "price", "category.name"],
  "filters": { "logic": "and", "children": [
    { "logic": "or", "children": [
      { "field": "category.name", "op": "EQ", "value": "Elektronika" },
      { "field": "category.name", "op": "EQ", "value": "Audio" }
    ] },
    { "logic": "or", "children": [
      { "field": "price", "op": "LT", "value": 100 },
      { "field": "name", "op": "ILIKE", "value": "pro" }
    ] }
  ] },
  "sort": [{ "field": "category.name", "direction": "ASC" }, { "field": "id", "direction": "ASC" }],
  "page": { "number": 0, "size": 20 }
}' | jq '{total: .page.totalElements, rows: [.rows[] | {id, name, "category.name"}]}'

# --- e) OR touching a nested field ----------------------------------------
hr
echo "e) OR z warunkiem na polu zagnieżdżonym:"
echo "   category.name = 'Elektronika' OR name ILIKE 'pro'"
echo "   Oczekiwane: ~22 wiersze. Produkty bez kategorii (51 'Pro Drone X',"
echo "   52 'Pocket Pro Mic') wchodzą przez 2. gałąź - semantyka LEFT JOIN + OR."
query '{
  "select": ["id", "name", "category.name"],
  "filters": { "logic": "or", "children": [
    { "field": "category.name", "op": "EQ", "value": "Elektronika" },
    { "field": "name", "op": "ILIKE", "value": "pro" }
  ] },
  "sort": [{ "field": "id", "direction": "ASC" }],
  "page": { "number": 0, "size": 100 }
}' | jq '{total: .page.totalElements, noCategory: [.rows[] | select(.["category.name"] == null) | {id, name}]}'

hr

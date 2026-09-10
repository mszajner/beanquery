#!/usr/bin/env bash
#
# AND/OR filter-tree and cross-module reference examples for the beanquery demo,
# for people without IntelliJ. Mirrors the "Filtry AND/OR (drzewo)" (b-e) and
# "Referencje między modułami" (ref-1..ref-5) sections of demo.http.
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

order_query() {
  curl -sS -X POST "$BASE/order/query" \
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

# --- Referencje między modułami (order.customer.* via ReferenceResolver) ------
# Moduł "order" trzyma tylko customer_id; nazwę/tier dostarcza moduł "customer"
# (tabela customer, czytana JdbcTemplate). Seed: 4 klientów (1 Acme Corp/gold,
# 2 Beta Industries/silver, 3 Ceres Ltd/gold, 4 Delta LLC/bronze) i 10 zamówień -
# id 8, 9 mają customer_id NULL, id 10 wskazuje na nieistniejącego klienta (99).

hr
echo "ref-1) Select z polami referencyjnymi - jedno batchowe resolve() na stronę."
echo "   id 1/2 -> 'Acme Corp'; id 8/9 -> null (NULL customer_id); id 10 -> null (99 nie istnieje)."
order_query '{
  "select": ["id", "status", "total", "customer.name", "customer.tier"],
  "sort": [{ "field": "id", "direction": "ASC" }],
  "page": { "number": 0, "size": 50 }
}' | jq '{total: .page.totalElements, rows: [.rows[] | {id, status, name: .["customer.name"], tier: .["customer.tier"]}]}'

hr
echo "ref-2) Filtr po polu referencyjnym -> resolveFilter() -> customer_id IN (...)."
echo "   'acme' pasuje do klienta 1 -> zamówienia 1 i 2. Oczekiwane: total 2."
order_query '{
  "select": ["id", "customer.name"],
  "filters": { "field": "customer.name", "op": "ILIKE", "value": "acme" },
  "page": { "number": 0, "size": 50 }
}' | jq '{total: .page.totalElements, ids: [.rows[].id]}'

hr
echo "ref-3) Filtr, który nie pasuje do nikogo -> 0 wierszy, totalElements 0 (NIE wszystkie!)."
echo "   Pusta rozdzielczość referencji = warunek zawsze fałszywy, nie 'brak filtra'."
order_query '{
  "select": ["id"],
  "filters": { "field": "customer.name", "op": "ILIKE", "value": "nobody" },
  "page": { "number": 0, "size": 50 }
}' | jq '{total: .page.totalElements, ids: [.rows[].id]}'

hr
echo "ref-4) Filtr referencyjny w gałęzi OR - przetłumaczone IN wstawione tylko tam."
echo "   (customer.name ILIKE 'acme' -> {1,2}) OR (status = 'NEW' -> {1,3,6,8}) -> {1,2,3,6,8} = 5."
order_query '{
  "select": ["id", "status", "customer.name"],
  "filters": { "logic": "or", "children": [
    { "field": "customer.name", "op": "ILIKE", "value": "acme" },
    { "field": "status", "op": "EQ", "value": "NEW" }
  ] },
  "sort": [{ "field": "id", "direction": "ASC" }],
  "page": { "number": 0, "size": 50 }
}' | jq '{total: .page.totalElements, rows: [.rows[] | {id, status, name: .["customer.name"]}]}'

hr
echo "ref-5) 400 - sortowanie po polu referencyjnym jest niedozwolone."
curl -sS -o /dev/null -w 'HTTP %{http_code}\n' -X POST "$BASE/order/query" \
  -H 'Content-Type: application/json' \
  -d '{
  "select": ["id"],
  "sort": [{ "field": "customer.name", "direction": "ASC" }],
  "page": { "number": 0, "size": 10 }
}'

hr

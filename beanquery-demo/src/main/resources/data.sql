-- Categories -----------------------------------------------------------------
INSERT INTO category (id, name, active) VALUES
  (1, 'Elektronika',    TRUE),
  (2, 'Books',          TRUE),
  (3, 'Home & Kitchen', TRUE),
  (4, 'Garden',         FALSE),
  (5, 'Toys & Games',   TRUE),
  (6, 'Sports',         TRUE),
  (7, 'Office',         TRUE),
  (8, 'Audio',          TRUE);

-- Products (50) --------------------------------------------------------------
INSERT INTO product (id, name, sku, price, stock, released_on, status, category_id) VALUES
  ( 1, 'ProBook 14',            'ELEC-0001',  1299.00,  42, DATE '2024-02-10', 'ACTIVE',       1),
  ( 2, 'ProBook 16',            'ELEC-0002',  1799.00,  18, DATE '2024-05-21', 'ACTIVE',       1),
  ( 3, 'UltraTab Mini',         'ELEC-0003',   449.00, 120, DATE '2023-11-03', 'ACTIVE',       1),
  ( 4, 'UltraTab Pro',          'ELEC-0004',   899.00,  35, DATE '2024-09-12', 'ACTIVE',       1),
  ( 5, 'Pixel Camera X',        'ELEC-0005',   699.00,   0, DATE '2022-06-30', 'DISCONTINUED', 1),
  ( 6, 'SmartHub Central',      'ELEC-0006',   129.00, 210, DATE '2024-01-15', 'ACTIVE',       1),
  ( 7, 'Wireless Charger Pad',  'ELEC-0007',    39.00, 480, DATE '2023-08-19', 'ACTIVE',       1),
  ( 8, 'Mechanical Keyboard',   'ELEC-0008',    89.00,  96, DATE '2024-03-08', 'ACTIVE',       1),
  ( 9, '4K Monitor 27',         'ELEC-0009',   329.00,  27, DATE '2024-07-02', 'ACTIVE',       1),
  (10, 'USB-C Dock Pro',        'ELEC-0010',   149.00,  61, DATE '2025-01-20', 'DRAFT',        1),
  (11, 'The Pragmatic Coder',   'BOOK-0001',    42.00, 150, DATE '2023-03-14', 'ACTIVE',       2),
  (12, 'Clean Queries',         'BOOK-0002',    38.50,  88, DATE '2024-04-01', 'ACTIVE',       2),
  (13, 'JPA in Practice',       'BOOK-0003',    45.00,  40, DATE '2024-10-18', 'ACTIVE',       2),
  (14, 'Spring Deep Dive',      'BOOK-0004',    54.00,  12, DATE '2025-02-11', 'ACTIVE',       2),
  (15, 'Algorithms Illustrated','BOOK-0005',    36.00, 300, DATE '2022-09-09', 'ACTIVE',       2),
  (16, 'Refactoring Legacy',    'BOOK-0006',    41.00,   5, DATE '2021-12-01', 'DISCONTINUED', 2),
  (17, 'The SQL Field Guide',   'BOOK-0007',    29.99, 175, DATE '2024-06-25', 'ACTIVE',       2),
  (18, 'Distributed Systems',   'BOOK-0008',    58.00,  22, DATE '2024-08-30', 'ACTIVE',       2),
  (19, 'Chef Knife 8"',         'HOME-0001',    59.00, 140, DATE '2023-05-17', 'ACTIVE',       3),
  (20, 'Pro Kettle 1.7L',       'HOME-0002',    45.00,  75, DATE '2024-02-28', 'ACTIVE',       3),
  (21, 'Cast Iron Skillet',     'HOME-0003',    39.00, 200, DATE '2022-11-11', 'ACTIVE',       3),
  (22, 'Espresso Machine Pro',  'HOME-0004',   649.00,   9, DATE '2024-11-05', 'ACTIVE',       3),
  (23, 'Non-stick Pan Set',     'HOME-0005',    89.00,  48, DATE '2024-03-19', 'ACTIVE',       3),
  (24, 'Food Storage 20pc',     'HOME-0006',    24.99, 360, DATE '2023-07-22', 'ACTIVE',       3),
  (25, 'Stand Mixer',           'HOME-0007',   329.00,  16, DATE '2025-03-04', 'DRAFT',        3),
  (26, 'Garden Hose 30m',       'GARD-0001',    34.00,  90, DATE '2023-04-05', 'ACTIVE',       4),
  (27, 'Pruning Shears Pro',    'GARD-0002',    28.00, 130, DATE '2024-04-14', 'ACTIVE',       4),
  (28, 'Raised Bed Kit',        'GARD-0003',    79.00,  20, DATE '2024-05-02', 'ACTIVE',       4),
  (29, 'Compost Bin 300L',      'GARD-0004',    64.00,   0, DATE '2022-08-08', 'DISCONTINUED', 4),
  (30, 'Solar Path Lights x6',  'GARD-0005',    41.00, 260, DATE '2024-06-11', 'ACTIVE',       4),
  (31, 'Building Blocks 500',   'TOYS-0001',    49.00, 400, DATE '2023-10-01', 'ACTIVE',       5),
  (32, 'RC Rally Car Pro',      'TOYS-0002',    89.00,  55, DATE '2024-07-19', 'ACTIVE',       5),
  (33, 'Wooden Train Set',      'TOYS-0003',    59.00,  70, DATE '2024-01-27', 'ACTIVE',       5),
  (34, 'Strategy Board Game',   'TOYS-0004',    44.00, 110, DATE '2024-09-30', 'ACTIVE',       5),
  (35, 'Plush Dragon',          'TOYS-0005',    22.00, 500, DATE '2022-05-12', 'ACTIVE',       5),
  (36, 'Puzzle 1000pc',         'TOYS-0006',    16.99, 340, DATE '2024-12-15', 'ACTIVE',       5),
  (37, 'Yoga Mat Pro',          'SPRT-0001',    39.00, 180, DATE '2023-02-20', 'ACTIVE',       6),
  (38, 'Adjustable Dumbbell',   'SPRT-0002',   199.00,  24, DATE '2024-03-25', 'ACTIVE',       6),
  (39, 'Running Shoes Flyweight','SPRT-0003',  129.00,  66, DATE '2024-08-07', 'ACTIVE',       6),
  (40, 'Trail Backpack 40L',    'SPRT-0004',    99.00,  33, DATE '2024-05-16', 'ACTIVE',       6),
  (41, 'Bicycle Helmet Pro',    'SPRT-0005',    79.00,   0, DATE '2022-07-01', 'DISCONTINUED', 6),
  (42, 'Resistance Band Set',   'SPRT-0006',    25.00, 290, DATE '2024-10-10', 'ACTIVE',       6),
  (43, 'Standing Desk 140cm',   'OFFC-0001',   429.00,  14, DATE '2024-02-05', 'ACTIVE',       7),
  (44, 'Ergonomic Chair Pro',   'OFFC-0002',   349.00,  19, DATE '2024-06-18', 'ACTIVE',       7),
  (45, 'Desk Lamp LED',         'OFFC-0003',    34.00, 220, DATE '2023-09-27', 'ACTIVE',       7),
  (46, 'Monitor Arm Dual',      'OFFC-0004',    89.00,  47, DATE '2024-11-22', 'ACTIVE',       7),
  (47, 'Noise Cancelling Pro',  'AUDI-0001',   279.00,  38, DATE '2024-04-09', 'ACTIVE',       8),
  (48, 'Studio Monitor Pair',   'AUDI-0002',   399.00,  11, DATE '2024-07-30', 'ACTIVE',       8),
  (49, 'Portable Speaker Mini', 'AUDI-0003',    59.00, 175, DATE '2023-06-14', 'ACTIVE',       8),
  (50, 'Turntable Classic',     'AUDI-0004',   249.00,   6, DATE '2025-04-02', 'DRAFT',        8);

-- Targeted rows for the AND/OR demo (see demo.http / demo.curl.sh) -----------
--   51,52,53 have no category  -> LEFT JOIN + OR semantics
--   54 is a cheap Elektronika item; 51/55 are expensive non-Elektronika items
--   -> (price < 50 OR category = Elektronika) is a strict superset of the AND
--   "Pro..." names are spread across categories and the no-category rows
INSERT INTO product (id, name, sku, price, stock, released_on, status, category_id) VALUES
  (51, 'Pro Drone X',      'DEMO-01', 1450.00,  15, DATE '2024-05-10', 'ACTIVE', NULL),
  (52, 'Pocket Pro Mic',   'DEMO-02',   22.00, 180, DATE '2024-02-01', 'ACTIVE', NULL),
  (53, 'Mystery Bundle',   'DEMO-03',  310.00,   9, DATE '2023-11-20', 'ACTIVE', NULL),
  (54, 'Nano Cable',       'DEMO-04',    9.00, 700, DATE '2024-01-05', 'ACTIVE', 1),
  (55, 'Pro Server Rack',  'DEMO-05',  980.00,   3, DATE '2024-08-15', 'ACTIVE', 7);

-- customer module -----------------------------------------------------------
INSERT INTO customer (id, name, tier) VALUES
  (1, 'Acme Corp',      'gold'),
  (2, 'Beta Industries','silver'),
  (3, 'Ceres Ltd',      'gold'),
  (4, 'Delta LLC',      'bronze');

-- order module (customer_id is a plain column, no FK constraint) -------------
--   ids 8,9 have a NULL customer_id; id 10 points at a non-existent customer (99)
INSERT INTO customer_order (id, status, total, customer_id) VALUES
  ( 1, 'NEW',      120.00, 1),
  ( 2, 'PAID',     340.00, 1),
  ( 3, 'NEW',       55.00, 2),
  ( 4, 'SHIPPED',  900.00, 3),
  ( 5, 'PAID',      72.50, 3),
  ( 6, 'NEW',      210.00, 4),
  ( 7, 'CANCELLED', 18.00, 2),
  ( 8, 'NEW',      460.00, NULL),
  ( 9, 'PAID',     130.00, NULL),
  (10, 'SHIPPED',  999.00, 99);

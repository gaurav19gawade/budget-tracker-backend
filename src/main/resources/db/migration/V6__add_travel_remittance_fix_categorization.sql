-- V6__add_travel_remittance_fix_categorization.sql
--
-- 1. Add Travel category (Delta Air, flights, hotels — not Entertainment)
-- 2. Add Remittance category (Western Union, international family transfers)
-- 3. Fix Transfer category: ensure isExcluded = true
--    (spouse Zelle + own account moves must not count as income or expense)
-- 4. Fix Costco Gas: currently categorized as Groceries → re-assign to Gas
--    for any transaction with "COSTCO GAS" in the merchant name
-- 5. Fix existing internal transfers sitting in Misc → move to Transfer
-- All safe to re-run (WHERE NOT EXISTS / WHERE EXISTS guards).

-- ─────────────────────────────────────────────
-- PART 1: New categories
-- ─────────────────────────────────────────────

INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Travel', '✈️', '#0ea5e9', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'travel'
);

-- Remittance: money sent overseas to family. Excluded so it doesn't
-- inflate spending in categories like Misc or Shopping.
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Remittance', '🌍', '#8b5cf6', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'remittance'
);

-- Bank Fee: overdraft fees, wire fees, account fees
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Bank Fee', '🏦', '#64748b', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'bank fee'
);

-- ─────────────────────────────────────────────
-- PART 2: Ensure Transfer is excluded
-- Transfer category must be isExcluded=true — spouse Zelle and own-account
-- moves are neither income nor expense, just money changing pockets.
-- ─────────────────────────────────────────────

UPDATE categories
SET is_excluded = true, updated_at = NOW()
WHERE LOWER(name) = 'transfer'
  AND is_excluded = false;

-- ─────────────────────────────────────────────
-- PART 3: Fix Costco Gas transactions → Gas category
-- Costco Gas was incorrectly auto-categorized as Groceries.
-- Re-assign any transaction where merchant contains "COSTCO GAS".
-- ─────────────────────────────────────────────

UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'gas'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%costco gas%'
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'gas'
  );

-- ─────────────────────────────────────────────
-- PART 4: Fix Delta Air → Travel (currently Entertainment)
-- ─────────────────────────────────────────────

UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'travel'
    LIMIT 1
),
updated_at = NOW()
WHERE (LOWER(t.merchant_name) LIKE '%delta air%'
    OR LOWER(t.merchant_name) LIKE '%united air%'
    OR LOWER(t.merchant_name) LIKE '%american air%'
    OR LOWER(t.merchant_name) LIKE '%southwest air%'
    OR LOWER(t.merchant_name) LIKE '%jetblue%')
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'travel'
  );

-- ─────────────────────────────────────────────
-- PART 5: Fix Western Union → Remittance
-- ─────────────────────────────────────────────

UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'remittance'
    LIMIT 1
),
updated_at = NOW()
WHERE LOWER(t.merchant_name) LIKE '%western union%'
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'remittance'
  );

-- ─────────────────────────────────────────────
-- PART 6: Fix Overdraft / bank fees → Bank Fee
-- ─────────────────────────────────────────────

UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'bank fee'
    LIMIT 1
),
updated_at = NOW()
WHERE (LOWER(t.merchant_name) LIKE '%overdraft%'
    OR LOWER(t.merchant_name) LIKE '%insufficient funds%'
    OR LOWER(t.merchant_name) LIKE '%monthly fee%'
    OR LOWER(t.merchant_name) LIKE '%account fee%')
  AND t.is_manual = false
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'bank fee'
  );

-- ─────────────────────────────────────────────
-- PART 7: Fix online transfers → Transfer category
-- Chase savings ↔ checking transfers sitting as null or Misc
-- ─────────────────────────────────────────────

UPDATE transactions t
SET category_id = (
    SELECT c.id FROM categories c
    WHERE c.user_id = t.user_id
      AND LOWER(c.name) = 'transfer'
    LIMIT 1
),
updated_at = NOW()
WHERE (LOWER(t.merchant_name) LIKE '%online transfer to%'
    OR LOWER(t.merchant_name) LIKE '%online transfer from%')
  AND t.is_manual = false
  AND t.category_id IS NULL
  AND EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = t.user_id AND LOWER(c.name) = 'transfer'
  );

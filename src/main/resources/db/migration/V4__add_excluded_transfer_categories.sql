-- V4__add_excluded_transfer_categories.sql
--
-- Ensures every user has "Credit Card Payment" and "Transfer" categories
-- marked isExcluded=true so they don't inflate income or expense totals.
--
-- These categories exist for transactions like:
--   - Paying your Chase credit card bill from checking (credit on checking = NOT real income)
--   - Zelle / ACH between your own accounts (both sides cancel out)
--
-- Safe to re-run: INSERT ... WHERE NOT EXISTS pattern.

INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT
    u.id,
    'Credit Card Payment',
    '💳',
    '#6366f1',
    true,
    NOW(),
    NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = u.id
      AND LOWER(c.name) = 'credit card payment'
);

INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT
    u.id,
    'Transfer',
    '↔️',
    '#8b5cf6',
    true,
    NOW(),
    NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c
    WHERE c.user_id = u.id
      AND LOWER(c.name) = 'transfer'
);

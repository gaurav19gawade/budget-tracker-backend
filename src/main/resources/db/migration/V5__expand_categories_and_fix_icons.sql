-- V5__expand_categories_and_fix_icons.sql
--
-- 1. Fixes incorrect icons on existing categories
-- 2. Adds missing expense categories (Mortgage, Childcare, etc.)
-- 3. Adds excluded utility categories (Salary, Investments, Refund)
--
-- All INSERTs use WHERE NOT EXISTS — safe to re-run.
-- Icon corrections use UPDATE WHERE EXISTS — idempotent.

-- ─────────────────────────────────────────────
-- PART 1: Fix icons on existing categories
-- ─────────────────────────────────────────────

-- Groceries: was 🛒 (same as Amazon) → 🥬
UPDATE categories
SET icon = '🥬', updated_at = NOW()
WHERE LOWER(name) = 'groceries'
  AND icon = '🛒';

-- Amazon: was 🛒 (same as Groceries) → 📦
UPDATE categories
SET icon = '📦', updated_at = NOW()
WHERE LOWER(name) = 'amazon'
  AND icon = '🛒';

-- Zelle: was 🎬 (Entertainment icon, wrong) → 💸
UPDATE categories
SET icon = '💸', updated_at = NOW()
WHERE LOWER(name) = 'zelle';

-- Internet: was 🎮 (gamepad, wrong) → 📡
UPDATE categories
SET icon = '📡', updated_at = NOW()
WHERE LOWER(name) = 'internet';

-- Misc: was 📱 (phone, misleading) → 🗂️
UPDATE categories
SET icon = '🗂️', updated_at = NOW()
WHERE LOWER(name) = 'misc';

-- Credit Card Payment: was 💪 (wrong) → 💳
UPDATE categories
SET icon = '💳', updated_at = NOW()
WHERE LOWER(name) = 'credit card payment'
  AND icon != '💳';

-- HOA: was 🏠 → 🏘️ (multi-unit, more accurate)
UPDATE categories
SET icon = '🏘️', updated_at = NOW()
WHERE LOWER(name) = 'hoa';

-- ─────────────────────────────────────────────
-- PART 2: New expense categories (isExcluded = false)
-- ─────────────────────────────────────────────

-- Mortgage — M&T Bank, NSM/Mr.Cooper, PatricianAsso rent
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Mortgage', '🏡', '#0ea5e9', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'mortgage'
);

-- Childcare — Goddard School, Cadence Education
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Childcare', '👶', '#f472b6', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'childcare'
);

-- Nanny — recurring Zelle payments to caregivers (Pavithra, Baeko)
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Nanny', '🧑‍🍼', '#c084fc', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'nanny'
);

-- Car Payment — HMF Hyundai Motor Finance
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Car Payment', '🚗', '#64748b', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'car payment'
);

-- Utilities — National Grid (NGRID06), Verizon
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Utilities', '💡', '#fbbf24', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'utilities'
);

-- Gas — Norwood Citgo, Costco Gas
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Gas', '⛽', '#f97316', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'gas'
);

-- Transportation — Lyft, EZPass, SpotHero, Logan Parking
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Transportation', '🚕', '#6366f1', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'transportation'
);

-- Healthcare — PatientFi medical financing, medical expenses
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Healthcare', '🏥', '#10b981', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'healthcare'
);

-- Donations — World Food Program, charitable giving
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Donations', '🤝', '#059669', false, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'donations'
);

-- ─────────────────────────────────────────────
-- PART 3: Excluded categories (don't count as income OR expense)
-- ─────────────────────────────────────────────

-- Salary — tag payroll credits so they're identifiable but excluded from
-- expense totals. Income is calculated separately from transactionType = 'credit'.
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Salary', '💰', '#22c55e', true, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'salary'
);

-- Investments — Robinhood debits/credits, Fidelity, Aspora.
-- Excluded from both income and expense so investment activity doesn't
-- distort household spending or earnings numbers.
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Investments', '📈', '#3b82f6', true, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'investments'
);

-- Refund — credits on the Sapphire Reserve credit card (Lyft refund, Saks return,
-- Fandango credit, Peloton statement credit, Amazon returns).
-- These are NOT income — they reduce a prior expense. Excluded so they don't
-- inflate the monthly earned figure.
INSERT INTO categories (user_id, name, icon, color, is_excluded, created_at, updated_at)
SELECT u.id, 'Refund', '🔄', '#94a3b8', true, NOW(), NOW()
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM categories c WHERE c.user_id = u.id AND LOWER(c.name) = 'refund'
);

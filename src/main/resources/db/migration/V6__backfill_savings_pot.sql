-- Statements imported before pot transfers were categorised as SAVINGS still hold them
-- as TRANSFER, which is why a savings pot calculated from them reads zero.
--
-- Only adapter-assigned rows are touched, so a category a user set by hand is never
-- overwritten. Monzo writes the pot's name into the description of a pot movement,
-- which is the only marker those rows carry.
UPDATE transactions
SET category = 'SAVINGS'
WHERE category = 'TRANSFER'
  AND category_source = 'ADAPTER'
  AND description ILIKE '%pot%';

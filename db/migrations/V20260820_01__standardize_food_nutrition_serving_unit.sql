-- Standardize catalog nutrition references to the frontend quantity contract.
-- Existing values were stored per 100 g; the same numeric snapshot is now treated
-- as one catalog serving so meal requests can consistently use amount + '인분'.
UPDATE food_nutritions
SET reference_amount = 1.00,
    reference_unit = '인분';

ALTER TABLE food_nutritions
    ADD CONSTRAINT chk_food_nutritions_serving_reference CHECK (
        reference_amount = 1.00 AND reference_unit = '인분'
    );

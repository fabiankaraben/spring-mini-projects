-- Add the department column to the employees table
-- Set a default value for existing rows using a two-step process:

ALTER TABLE employees ADD COLUMN department VARCHAR(50);
UPDATE employees SET department = 'Engineering';
ALTER TABLE employees ALTER COLUMN department SET NOT NULL;

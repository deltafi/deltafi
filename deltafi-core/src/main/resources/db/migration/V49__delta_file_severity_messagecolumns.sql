ALTER TABLE delta_files
   ADD COLUMN warnings boolean DEFAULT FALSE,
   ADD COLUMN user_notes boolean DEFAULT FALSE,
   ADD COLUMN messages jsonb;

-- BaseEntity PK 타입을 bigint → uuid 로 변경
ALTER TABLE sample ALTER COLUMN id DROP IDENTITY IF EXISTS;
ALTER TABLE sample DROP CONSTRAINT sample_pkey;
ALTER TABLE sample ALTER COLUMN id TYPE uuid USING gen_random_uuid();
ALTER TABLE sample ADD PRIMARY KEY (id);

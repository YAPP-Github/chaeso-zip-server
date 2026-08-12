alter table channels
    add column if not exists tagline varchar(255);

-- company_name 을 varchar(255) → varchar(50) 로 축소한다.
alter table users
    alter column company_name type varchar(50);
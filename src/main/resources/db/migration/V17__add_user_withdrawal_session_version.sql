do $$
begin
    if exists (
        select lower(email)
        from users
        group by lower(email)
        having count(*) > 1
    ) then
        raise exception 'Cannot enforce unique user email: duplicate lower(email) values exist';
    end if;
end
$$;

alter table users
    add column session_version integer not null default 0;

drop index uq_users_email_active;
create unique index uq_users_email on users (lower(email));

create index idx_users_deleted_at
    on users (deleted_at)
    where deleted_at is not null;

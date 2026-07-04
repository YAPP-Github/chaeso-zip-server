create table users (
    id                   uuid         primary key,
    email                varchar(255) not null,
    email_verified       boolean      not null default false,
    nickname             varchar(50)  not null,
    employment_status    varchar(30)  not null,
    company_name         varchar(255),
    profile_image_url    varchar(500),
    occupation           varchar(20),
    last_login_at        timestamp,
    last_login_provider  varchar(20),
    terms_agreed         boolean      not null default false,
    terms_version        varchar(20),
    is_marketing_agreed  boolean      not null default false,
    marketing_agreed_at  timestamp,
    created_at           timestamp    not null,
    updated_at           timestamp    not null,
    deleted_at           timestamp
);

create unique index uq_users_email_active on users (lower(email)) where deleted_at is null;

create table auth_identities (
    id            uuid         primary key,
    user_id       uuid         not null references users(id),
    provider      varchar(20)  not null,
    provider_uid  varchar(255),
    password_hash varchar(100),
    last_login_at timestamp,
    created_at    timestamp    not null,
    constraint uq_auth_identities_user_provider unique (user_id, provider)
);

create unique index uq_auth_identities_social_provider_uid
    on auth_identities (provider, provider_uid) where provider_uid is not null;

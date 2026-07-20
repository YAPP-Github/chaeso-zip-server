alter table channel_pricing
    alter column segment      type varchar(255),
    alter column valid_period type varchar(255);

alter table source_documents
    alter column valid_period type varchar(255);
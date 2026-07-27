alter table onboarding_responses
    add column raw_file_urls text[];

alter table onboarding_ad_history_snapshots
    drop column raw_file_url_snap;

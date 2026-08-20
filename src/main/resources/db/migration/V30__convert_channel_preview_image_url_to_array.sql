alter table channels
    add column if not exists preview_image_urls text[];

update channels
set preview_image_urls = array [preview_image_url]
where preview_image_url is not null;

alter table channels
    drop column preview_image_url;

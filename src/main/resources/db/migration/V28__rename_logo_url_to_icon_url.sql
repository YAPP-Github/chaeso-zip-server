-- channels 및 channel_comparison_items 테이블의 logo_url 컬럼을 icon_url로 변경

ALTER TABLE channels RENAME COLUMN logo_url TO icon_url;
ALTER TABLE channel_comparison_items RENAME COLUMN logo_url_snap TO icon_url_snap;

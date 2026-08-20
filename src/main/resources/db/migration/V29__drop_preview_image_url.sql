-- icon_url로 대체되어 더 이상 쓰이지 않는 preview_image_url 컬럼 제거

ALTER TABLE channel_comparison_items DROP COLUMN preview_image_url_snap;
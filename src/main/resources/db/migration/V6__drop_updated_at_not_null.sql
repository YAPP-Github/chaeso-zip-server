-- updated_at 을 "최초 수정 전에는 null" 로 두기 위해 NOT NULL 제약 제거.
-- BaseTimeEntity.updated_at 이 nullable 로 변경된 것과 스키마를 일치시킨다.
alter table sample alter column updated_at drop not null;
alter table users  alter column updated_at drop not null;

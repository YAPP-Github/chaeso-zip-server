package chaeso.zip.server.user.domain;

import lombok.RequiredArgsConstructor;

/** 고용상태 */
@RequiredArgsConstructor
public enum EmploymentStatus {
  EMPLOYEE("직장인"),
  EXECUTIVE("임원/경영진"),
  SELF_EMPLOYED("개인사업자/소상공인"),
  FREELANCER("프리랜서/1인 창업자"),
  STUDENT_UNIVERSITY("대학생/대학원생"),
  JOB_SEEKER("취업준비생/구직자"),
  UNABLE_TO_WORK("무직/기타");

  private final String description;
}

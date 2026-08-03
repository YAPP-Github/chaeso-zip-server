package chaeso.zip.server.recommendation.domain;

import chaeso.zip.server.channel.domain.vo.Gender;

/**
 * 채널의 주요 타깃 표기. 소개서에 값이 없는 축은 좁혀 말하지 않고 전체로 표기한다.
 */
public final class PrimaryTarget {

  private static final String ALL_AGES = "전 연령";
  private static final String ALL_GENDERS = "전 성별";
  private static final String MALE = "남성";
  private static final String FEMALE = "여성";
  private static final String SEPARATOR = " ";

  private PrimaryTarget() {
  }

  /**
   * 주요 타깃 문구
   */
  public static String of(String primaryAgeBand, Gender primaryGender) {
    return ageText(primaryAgeBand) + SEPARATOR + genderText(primaryGender);
  }

  private static String ageText(String primaryAgeBand) {
    return primaryAgeBand == null || primaryAgeBand.isBlank() ? ALL_AGES : primaryAgeBand.trim();
  }

  private static String genderText(Gender primaryGender) {
    if (primaryGender == null) {
      return ALL_GENDERS;
    }
    return switch (primaryGender) {
      case MALE -> MALE;
      case FEMALE -> FEMALE;
      case ALL -> ALL_GENDERS;
    };
  }
}

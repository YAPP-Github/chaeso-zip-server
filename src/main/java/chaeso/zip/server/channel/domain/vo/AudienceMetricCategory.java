package chaeso.zip.server.channel.domain.vo;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 오디언스 규모 지표(metric_name)의 성격 분류
 */
public enum AudienceMetricCategory {

  /** 우선순위 순서대로 나열 **/
  ACTIVE_MAU("MAU"),
  ACTIVE_DAU("DAU"),
  MEMBER("회원", "가입자"),
  VISITOR("방문", "PV", "UV", "페이지뷰"),
  FOLLOWER("팔로워", "구독"),
  ETC;

  /** 지표명 부분일치 키워드. */
  private final List<String> keywords;

  AudienceMetricCategory(String... keywords) {
    this.keywords = Stream.of(keywords).map(AudienceMetricCategory::upperCased).toList();
  }

  /** 지표명을 분류한다. 어느 키워드에도 걸리지 않거나 지표명이 없으면 {@link #ETC}. */
  public static AudienceMetricCategory of(String metricName) {
    if (metricName == null) {
      return ETC;
    }
    String target = upperCased(metricName);
    for (AudienceMetricCategory category : values()) {
      if (category.matches(target)) {
        return category;
      }
    }
    return ETC;
  }

  private boolean matches(String upperCasedMetricName) {
    return keywords.stream().anyMatch(upperCasedMetricName::contains);
  }

  private static String upperCased(String value) {
    return value.toUpperCase(Locale.ROOT);
  }
}

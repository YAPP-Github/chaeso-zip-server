package chaeso.zip.server.channel.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AudienceMetricCategoryTest {

  @ParameterizedTest
  @CsvSource({
      "MAU, ACTIVE_MAU",
      "글로벌 MAU, ACTIVE_MAU",
      "앱 mau, ACTIVE_MAU",
      "DAU, ACTIVE_DAU",
      "평균 dau, ACTIVE_DAU",
      "누적 회원 수, MEMBER",
      "가입자수, MEMBER",
      "월 방문자 수, VISITOR",
      "월 PV, VISITOR",
      "순 uv, VISITOR",
      "일 페이지뷰, VISITOR",
      "팔로워 수, FOLLOWER",
      "구독자 수, FOLLOWER",
      "누적 거래액, ETC"})
  @DisplayName("지표명 키워드를 대소문자 구분 없이 부분일치로 분류한다")
  void classifiesByKeyword(String metricName, AudienceMetricCategory expected) {
    assertThat(AudienceMetricCategory.of(metricName)).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @DisplayName("지표명이 없으면 기타로 분류한다")
  void classifiesBlankMetricNameAsEtc(String metricName) {
    assertThat(AudienceMetricCategory.of(metricName)).isEqualTo(AudienceMetricCategory.ETC);
  }

  @ParameterizedTest
  @ValueSource(strings = {"MAU 회원 수", "MAU/DAU"})
  @DisplayName("여러 키워드에 걸리면 우선순위가 높은 카테고리를 택한다")
  void picksHighestPriorityCategoryOnMultipleMatches(String metricName) {
    assertThat(AudienceMetricCategory.of(metricName)).isEqualTo(AudienceMetricCategory.ACTIVE_MAU);
  }

  @Test
  @DisplayName("선언 순서가 대표 지표 선정 우선순위다")
  void declarationOrderIsSelectionPriority() {
    assertThat(AudienceMetricCategory.values()).containsExactly(
        AudienceMetricCategory.ACTIVE_MAU,
        AudienceMetricCategory.ACTIVE_DAU,
        AudienceMetricCategory.MEMBER,
        AudienceMetricCategory.VISITOR,
        AudienceMetricCategory.FOLLOWER,
        AudienceMetricCategory.ETC);
  }
}

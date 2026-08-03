package chaeso.zip.server.recommendation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import chaeso.zip.server.channel.domain.vo.Gender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

class PrimaryTargetTest {

  @ParameterizedTest
  @CsvSource({
      "20~40대, FEMALE, 20~40대 여성",
      "30대,    MALE,   30대 남성",
      "30대,    ALL,    30대 전 성별",   // 성별 편중이 없는 채널
      "'',      FEMALE, 전 연령 여성",   // 연령 정보가 없는 채널
  })
  @DisplayName("연령대와 성별을 합쳐 표기하고, 값이 없는 축은 전체로 표기한다")
  void combinesAgeAndGender(String primaryAgeBand, Gender primaryGender, String expected) {
    assertThat(PrimaryTarget.of(primaryAgeBand, primaryGender)).isEqualTo(expected);
  }

  @ParameterizedTest
  @NullSource
  @DisplayName("두 축 모두 값이 없으면 전 연령·전 성별로 표기한다")
  void fallsBackWhenNothingKnown(String primaryAgeBand) {
    assertThat(PrimaryTarget.of(primaryAgeBand, null)).isEqualTo("전 연령 전 성별");
  }
}

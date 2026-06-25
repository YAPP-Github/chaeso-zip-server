package chaeso.zip.server.sample.domain;

import chaeso.zip.server.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 컨벤션 예시용 샘플 애그리거트 루트.
 *
 * <p>엔티티 작성 규칙:
 * <ul>
 *   <li>{@code @NoArgsConstructor(PROTECTED)} 로 무분별한 생성 방지</li>
 *   <li>{@code public} 생성자 대신 정적 팩토리 메서드({@link #create(String)})로 생성</li>
 *   <li>상태 변경은 의미 있는 도메인 메서드({@link #rename(String)})로만 노출</li>
 *   <li>setter 를 두지 않는다</li>
 * </ul>
 */
@Getter
@Entity
@Table(name = "sample")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sample extends BaseEntity {

  @Column(nullable = false, length = 100)
  private String name;

  private Sample(String name) {
    this.name = name;
  }

  public static Sample create(String name) {
    return new Sample(name);
  }

  public void rename(String name) {
    this.name = name;
  }
}

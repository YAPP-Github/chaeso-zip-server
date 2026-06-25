package chaeso.zip.server.sample.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 샘플 애그리거트의 영속성 추상화. 도메인 계층에 인터페이스를 두고 Spring Data JPA 가 구현을 제공한다.
 */
public interface SampleRepository extends JpaRepository<Sample, Long> {

}

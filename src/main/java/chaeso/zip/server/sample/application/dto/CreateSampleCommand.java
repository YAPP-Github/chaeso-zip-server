package chaeso.zip.server.sample.application.dto;

/**
 * 샘플 생성 유스케이스의 입력 커맨드. 애플리케이션 계층의 입력 경계로, 표현 계층의 요청 DTO 와 분리한다.
 *
 * <p>컨벤션:
 * <ul>
 *   <li>애플리케이션 서비스의 입력은 원시 타입 나열 대신 Command 객체로 받는다</li>
 *   <li>HTTP/검증 관심사(웹 어노테이션)는 표현 계층 요청 DTO 가, 유스케이스 입력 형태는 Command 가 담당</li>
 *   <li>표현 계층 요청 DTO 의 {@code toCommand()} 로 변환해 전달한다</li>
 * </ul>
 */
public record CreateSampleCommand(String name) {

}

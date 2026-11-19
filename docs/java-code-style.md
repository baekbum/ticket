# Java 코드 스타일

직접 작성한 `src/main/java` 코드에 적용한다. Querydsl의 `src/main/generated` 코드는 생성기가 관리한다.

## 클래스 멤버 순서

컨트롤러, 서비스, 레포지토리는 다음 순서로 작성한다. 같은 그룹에서는 기존 선언 순서를 유지한다.

1. 클래스 필드와 초기화 블록
2. 명시적 생성자
3. Create: 생성·등록·저장 (`insert`, `create`, `save` 등)
4. Read: 조회·검색 (`select`, `search`, `find`, `get` 등)
5. Update: 변경·상태 갱신 (`update`, `change`, `complete` 등)
6. Delete: 삭제·제거·탈퇴 (`delete`, `remove`, `withdraw` 등)
7. 기타 메서드와 내부 타입

메서드는 실제 역할에 따라 분류한다. HTTP POST를 사용하는 조회 메서드도 Read에 둔다. 내부 처리를 위한 private 보조 메서드는 기타 메서드에 둔다. 필드와 초기화 블록의 상대 순서는 유지한다.

## 지역 변수 타입

- PG 서비스의 프로덕션 코드에서는 지역 변수에 명시적인 타입을 작성한다. `var` 타입 추론을 사용하지 않는다.
- HTTP 응답도 `ResponseEntity<응답DTO>`처럼 실제 반환 타입을 드러낸다.

## 조회 메서드 이름

- PG 컨트롤러의 조회 메서드는 `find`로 시작해 조회임을 명확히 드러낸다. 예: `findGatewayDeposit`, `findGatewayCancellation`, `findStatus`, `findApprovalExists`.
- 조회와 상태 변경을 함께 수행하는 업무 메서드는 단순 조회로 이름 붙이지 않는다. 예: 환불의 확인·종료를 함께 처리하는 `resolve`.
- 메서드 이름을 정리할 때 기존 API URL과 요청·응답 계약은 유지한다.

## 줄바꿈

- 메서드와 메서드 사이에는 빈 줄을 한 줄 둔다. 어노테이션과 주석은 해당 메서드에 붙인다.
- 메서드 선언이 120자를 넘으면 인자를 한 줄에 하나씩 작성한다. 여러 줄로 작성한 선언의 닫는 괄호는 별도 줄에 둔다.
- 메서드 본문의 여는 중괄호 다음에는 빈 줄을 한 줄 두고 코드를 시작한다.
- 메서드 내부에서도 인증 확인, 요청 검증, 값 준비, 처리 실행, 결과 반환 등 의미가 바뀌는 지점에는 빈 줄을 한 줄 둔다. 검증문과 앞뒤 코드, `try`·반복문과 앞뒤 코드도 같은 기준으로 구분한다.
- 같은 역할의 연속된 필드 대입이나 하나의 응답을 구성하는 코드는 붙여 쓴다. 모든 문장 사이에 일괄적으로 빈 줄을 넣지는 않는다.
- 컨트롤러·서비스·레포지토리의 연속된 필드 선언 사이에는 빈 줄을 두지 않는다.
- 엔티티·임베디드 객체·DTO·VO·데이터 전달용 record는 각 필드 선언 사이에 빈 줄을 한 줄 둔다. 필드와 메서드 사이, 메서드와 메서드 사이에도 빈 줄을 한 줄 둔다.
- 설명 주석과 어노테이션은 해당 필드나 메서드에 붙인다. 이전 선언과 다음 설명 주석 사이를 빈 줄로 구분한다.
- 같은 블록에 연속해서 등장하는 독립적인 `if` 문 사이에는 빈 줄을 한 줄 둔다. `else if`는 연결해서 작성한다.

```java
// 사용자 데이터를 조회하고 저장하는 레포지토리.
private final UserRepository repository;
// 비밀번호를 암호화하고 일치 여부를 확인하는 인코더.
private final PasswordEncoder passwordEncoder;

/**
 * 사용자 정보를 수정한다.
 */
public UserResponse update(
        String userId,
        UpdateUserRequest request
) {

    if (userId == null) {
        throw new IllegalArgumentException("사용자 ID는 필수입니다.");
    }

    if (request == null) {
        throw new IllegalArgumentException("수정할 정보는 필수입니다.");
    }

    return repository.update(userId, request).toResponse();
}
```

## 주석

- 서비스 계층의 모든 필드는 `//` 주석으로 역할을 설명한다.
- 서비스 계층의 모든 메서드는 접근 제어자와 관계없이 `/** ... */` 형식의 Javadoc을 작성한다.
- 엔티티, 임베디드 객체, DTO, VO와 데이터 전달용 record의 모든 필드와 직접 선언한 메서드는 `//` 주석으로 역할을 설명한다. 여러 줄이 필요하면 각 줄에 `//`를 붙인다.
- 서비스 내부에 선언한 DTO와 record에도 데이터 객체의 `//` 주석 규칙을 적용한다.
- 기존 주석에 제약 조건이나 처리 이유가 있으면 보존한다. Lombok 등으로 생성되는 메서드는 원본 코드의 주석 작성 대상에 포함하지 않는다.

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

## 줄바꿈

- 메서드와 메서드 사이에는 빈 줄을 한 줄 둔다. 어노테이션과 주석은 해당 메서드에 붙인다.
- 메서드 선언이 120자를 넘으면 인자를 한 줄에 하나씩 작성한다. 여러 줄로 작성한 선언의 닫는 괄호는 별도 줄에 둔다.
- 메서드 본문의 여는 중괄호 다음에는 빈 줄을 한 줄 두고 코드를 시작한다.
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

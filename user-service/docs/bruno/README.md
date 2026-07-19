# user-service Bruno API 테스트

Bruno에서 아래 폴더를 열면 됩니다.

```text
user-service/docs/bruno
```

환경은 실행 방식에 맞춰 선택합니다.

- `local-direct`: user-service를 `8081` 포트로 직접 호출할 때 사용
- `local-ingress`: 로컬 ingress의 `80` 포트를 통해 호출할 때 사용

사용 순서:

1. `auth-service/docs/bruno`에서 `Admin Login` 또는 `User Login`을 먼저 실행합니다.
2. 발급된 `accessToken`을 이 컬렉션의 선택한 환경에 붙여 넣습니다.
3. `Select Me`, `Select Users`, 주소 관련 요청을 실행합니다.

환경변수:

- `userBaseUrl`: user-service API 기본 URL
- `accessToken`: 인증 요청에 사용할 JWT access token
- `userId`: 조회/수정 샘플에 사용할 사용자 ID
- `addressId`: 주소 조회/수정 샘플에 사용할 주소 ID

관리자 API는 `/manage` 경로를 사용합니다. 일반 사용자 API는 `/select/me`, `/address/select/me`처럼 로그인 사용자 기준으로 동작합니다.

삭제, 비밀번호 초기화, 일괄 삭제 요청은 실제 데이터를 변경합니다. 실행 전에 `userId`, `addressId`, 요청 body 값을 확인하세요.

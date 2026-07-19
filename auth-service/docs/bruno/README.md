# auth-service Bruno API 테스트

Bruno에서 아래 폴더를 열면 됩니다.

```text
auth-service/docs/bruno
```

환경은 실행 방식에 맞춰 선택합니다.

- `local-direct`: auth-service를 `8080` 포트로 직접 호출할 때 사용
- `local-ingress`: 로컬 ingress의 `80` 포트를 통해 호출할 때 사용

사용 순서:

1. `Admin Login` 또는 `User Login`을 먼저 실행합니다.
2. 로그인 응답의 `accessToken`, `refreshToken`이 선택한 환경변수에 저장됩니다.
3. 저장된 토큰으로 `Validate Token`, `Reissue Token` 요청을 실행할 수 있습니다.

새 컬렉션을 만들 필요 없이 Bruno의 `Open Collection`으로 이 폴더를 열면 됩니다.

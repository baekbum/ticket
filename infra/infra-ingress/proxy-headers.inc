proxy_set_header Host $host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
proxy_set_header X-Forwarded-Proto $scheme;
proxy_set_header Authorization $http_authorization;
# 브라우저가 보낸 신원 헤더는 서비스에 전달하지 않습니다.
proxy_set_header X-User-Id "";
proxy_set_header X-User-Role "";

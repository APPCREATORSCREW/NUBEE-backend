![Java](https://img.shields.io/badge/Java-21-ED8B00?style=flat&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.0.7-6DB33F?style=flat&logo=springboot&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?style=flat&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-7-DC382D?style=flat&logo=redis&logoColor=white)
![AWS EC2](https://img.shields.io/badge/AWS_EC2-FF9900?style=flat&logo=amazonec2&logoColor=white)
![AWS RDS](https://img.shields.io/badge/AWS_RDS-527FFF?style=flat&logo=amazonrds&logoColor=white)
![AWS S3](https://img.shields.io/badge/AWS_S3-569A31?style=flat&logo=amazons3&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-2496ED?style=flat&logo=docker&logoColor=white)
![GitHub Actions](https://img.shields.io/badge/GitHub_Actions-2088FF?style=flat&logo=githubactions&logoColor=white)
![Nginx](https://img.shields.io/badge/Nginx-009639?style=flat&logo=nginx&logoColor=white)

# 🐝 Nubee Backend

> 초등학생 대상 뉴스 기반 경제·시사 학습 앱 **누비(Nubee)** 의 백엔드 서버입니다.

<br>

## 📌 프로젝트 소개

누비는 초등학생이 뉴스를 통해 경제·시사 어휘를 쉽고 재미있게 학습할 수 있도록 돕는 교육 앱입니다.
AI 기반 뉴스 콘텐츠 제공, 키워드 퀴즈, 플래시카드 학습, 포인트 및 스킨 시스템 등의 기능을 제공합니다.

<br>

## 👥 팀원 소개

| 이름 | 역할 | GitHub |
|------|------|--------|
| 김다은 | 인증/인가 · 단어장 도메인 · 배포 · 아키텍처 설계 · 인프라 구축  | [@daeunkim701](https://github.com/daeunkim701) |
| 이나연 | 뉴스 도메인 · LLM 연동 · 외부 News API 활용 메인 기능 개발 | [@nylee0116](https://github.com/nylee0116) |
| 성유민 | 프로필 · 포인트 · 복습 도메인 · AWS S3 연동 | [@jadeseeu](https://github.com/jadeseeu) |

<br>

## 🛠 기술 스택

### Backend
| 항목 | 내용 |
|------|------|
| Framework | Spring Boot 4.0.7 |
| Language | Java 21 |
| Build System | Gradle |
| JDK | Amazon Corretto 21 |

### Database & Storage
| 항목 | 내용 |
|------|------|
| Database | AWS RDS MySQL 8.4 |
| Cache | Redis 7 |
| Image Storage | AWS S3 |

### Infrastructure & DevOps
| 항목 | 내용 |
|------|------|
| Server | AWS EC2 (t3.small) |
| Container | Docker + Docker Compose |
| Image Registry | GHCR (GitHub Container Registry) |
| CI/CD | GitHub Actions |
| Reverse Proxy | Nginx + Let's Encrypt (HTTPS) |
| Monitoring | AWS CloudWatch |

### API & Tools
| 항목 | 내용 |
|------|------|
| API 문서화 | Swagger |
| API 테스트 | Postman |
| 부하 테스트 | K6 |

<br>

## 🏗 아키텍처

<img width="1280" height="720" alt="nubee-인프라 아키텍처04" src="https://github.com/user-attachments/assets/1dd8b908-b83d-47c1-8374-7823ca63265d" />

<br>


## 📁 프로젝트 구조

```
src/main/java/com/solux31/nubee_BE/
├── domain/
│   ├── auth/          # 인증/인가, 온보딩
│   ├── words/         # 단어장
│   ├── news/          # 뉴스 콘텐츠 제공
│   ├── review/        # 복습 탭
│   ├── points/        # 포인트
│   └── profile/       # 프로필, 스킨
└── global/
    ├── apiPayload/    # 공통 응답 형식, 예외 처리
    ├── config/        # Security, Swagger, Redis 설정
    ├── email/         # 이메일 발송
    └── security/      # JWT, 인증 필터
```

<br>

## 🔑 주요 기능

### 인증/인가
- JWT 기반 인증 (Access Token 1시간, Refresh Token 30일)
- Redis를 활용한 Refresh Token 관리 (TTL 자동 만료)
- 카카오 소셜 로그인 (OAuth 2.0)
- 이메일 인증 (비밀번호 찾기, 만 14세 미만 부모님 인증)
- Redis를 활용한 이메일 인증 코드 관리 (TTL 5분 자동 만료)

### 학습
- AI 기반 뉴스 콘텐츠 자동 생성 및 제공 (LLM + 외부 News API 연동)
- 키워드 퀴즈 (KEYWORD 타입) 및 뉴스 퀴즈 (NEWS 타입) 채점 및 포인트 지급
- 플래시카드 학습 (단어장 추가/삭제, 오늘 저장/이전 저장 분류)
- 오늘의 맞춤 키워드 제공 (유저 선호 키워드 개수 기반)
- 학습 결과 부모님께 전송 (키워드 목록, 뉴스 원문 링크, 퀴즈 정답률 포함)

### 부가 기능
- 포인트 및 레벨 시스템 (퀴즈 정답 시 포인트 적립, 50 포인트마다 레벨업)
- 스킨 시스템 (5 레벨마다 순서대로 스킨 잠금 해제, AWS S3 이미지 관리, 회원가입 시 기본 스킨 자동 지급)
- 복습 탭 (카테고리별 · 년월별 학습한 뉴스 목록 조회)
- 연속 학습 스트릭 관리

<br>

## 🚀 배포

### 배포 환경
```
AWS EC2 (t3.small, Ubuntu 22.04)
├── Spring Boot 컨테이너 (Docker)
├── Redis 컨테이너 (Docker)
├── Nginx (Reverse Proxy + HTTPS)
└── CloudWatch Agent
```

### CI/CD 흐름
```
Developer
→ GitHub (코드 push)
→ GitHub Actions (자동 빌드)
→ GHCR (Docker 이미지 push)
→ EC2 (이미지 pull + 자동 배포)
```

### 접속 정보
| 항목 | URL |
|------|------|
| 서버 | https://nubee.site |
| Swagger | https://nubee.site/swagger-ui/index.html |

<br>


## 📝 API 문서

- **Notion 명세서**: [바로가기](https://app.notion.com/p/33107ff4f52981a0b680f3b50edd01a5?v=38707ff4f529806bbbfc000c7b4e7200)
- **Swagger UI**: [바로가기](https://nubee.site/swagger-ui/index.html)

<br>

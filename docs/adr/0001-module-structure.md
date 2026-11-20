# ADR 0001: 기능별 멀티모듈 + 도메인 포트 구조

- 상태: 승인됨
- 날짜: 2026-11-20
- 관련: herezi-api `docs/product/TECH-PLAN.md` §2 "앱 아키텍처: 기능별 모듈 + 도메인 포트", 이슈 #1

## 배경

Herezi 안드로이드 앱은 에이전트가 주로 개발한다. 에이전트가 한 번에 볼 범위가 좁고,
잘못된 의존을 빌드가 즉시 잡아 주며, 서버 없이도 화면을 만들고 테스트할 수 있어야 한다.

## 결정

1. **모듈 구조**
   - `:core:domain` — 순수 Kotlin/JVM. 모델(`Spot`, `Category`, `When`)과 포트(`SpotsRepository`), `FakeSpotsRepository`.
   - `:core:network` — Retrofit + kotlinx.serialization. DTO는 `api/openapi.yaml` 그대로, 도메인으로 매핑. `LiveSpotsRepository`.
   - `:core:ui` — Compose 테마(잉크 #191F28, 흰 배경)와 표시용 문자열·포맷.
   - `:core:map` — 네이버 지도 SDK를 감싼 `SpotMap`. **지도 SDK는 이 모듈에서만** 쓴다.
   - `:feature:nearby`, `:feature:spotdetail` — 화면 + ViewModel. 서로 의존하지 않는다.
   - `:app` — 조립 루트. Hilt 바인딩, 내비게이션, 설정값(BuildConfig).
2. **의존 규칙은 빌드가 강제**한다: 루트 `build.gradle.kts`의 `checkModuleRules` (`make check`에 포함).
3. **데이터 선택**: `HEREZI_API_BASE_URL`이 비어 있으면 `FakeSpotsRepository`, 있으면 `LiveSpotsRepository`.
   키·서버 없이도 빌드·실행·테스트가 된다.
4. **DI는 Hilt**, 어노테이션 처리는 KSP. ViewModel은 `@HiltViewModel`, 저장소 바인딩은 `:app`의 `DataModule` 한 곳.
5. **지도 키**: 네이버 클라우드 Maps Client ID는 `local.properties`/환경 변수 `NAVER_MAP_CLIENT_ID` →
   manifest placeholder(`com.naver.maps.map.NCP_KEY_ID`). 없으면 빈 값으로 빌드되고 지도 대신 안내를 보여준다.

## 결과

- 좋음: feature 단위 테스트가 Fake로 빠르게 돈다. 지도 SDK 교체·업그레이드 영향이 `:core:map`에 갇힌다.
- 비용: 모듈 수만큼 빌드 설정이 늘어난다 → 공통 설정은 루트 `subprojects {}` 한 곳에 둔다.
- 나중에: 모듈이 더 늘면 convention plugin(`build-logic`)으로 옮긴다.

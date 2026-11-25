# AGENTS.md — herezi-android

에이전트(그리고 사람)가 이 레포에서 일할 때 먼저 읽는 문서. 짧게 유지한다.

## 목적

**Herezi(여기지)** 안드로이드 앱. 위치만으로 "지금 내 주변에 뭐가 있는지"(축제·공연·전시·팝업·자연 등)를
목록·지도로 보여주고, 상세에서 네이버지도 길찾기로 보낸다.

## 기준 문서 (herezi-api 레포, 읽기 전용)

| 문서 | 내용 |
|---|---|
| [SERVICE-PLAN.md](https://github.com/herezi/herezi-api/blob/develop/docs/product/SERVICE-PLAN.md) | 서비스 기획: 사용자, 카테고리 9종, 화면, 기능 우선순위 |
| [PRODUCT.md](https://github.com/herezi/herezi-api/blob/develop/docs/product/PRODUCT.md) | 제품 스펙 |
| [TECH-PLAN.md](https://github.com/herezi/herezi-api/blob/develop/docs/product/TECH-PLAN.md) | 기술 기획: 스택, 레포 구조, 개발 방식, 일정 |
| [api/openapi.yaml](https://github.com/herezi/herezi-api/blob/develop/api/openapi.yaml) | API 계약 — 이 레포의 `api/openapi.yaml`은 복사본 |

이 레포의 결정 기록: [`docs/adr/`](docs/adr/).

## 스택

Kotlin 2.0, Jetpack Compose(Material 3), Hilt(KSP), Retrofit + kotlinx.serialization, 네이버 지도 SDK
(`naver-map-compose`), minSdk 26 / compile·target 35, JDK 17, Gradle 8.11.

## 모듈 지도

```
:app ──▶ :feature:nearby ──▶ :core:map ──▶ :core:domain
   │        └────────────▶ :core:ui ───▶ :core:domain
   ├──▶ :feature:home · spotdetail · onboarding · saved · search · settings · auth ─▶ :core:ui, :core:domain
   ├──▶ :core:network ─────▶ :core:domain
   └──▶ :core:data ────────▶ :core:domain
```

| 모듈 | 역할 |
|---|---|
| `:core:domain` | 순수 Kotlin. `Spot`, `Category`(9종), `When`, `SpotQuery`(`keyword`=검색 `q`), `SpotQueryEngine`(기기 조회: 하버사인·KST 기간·필터·bbox·거리순, 서버 `when.go` 이식), `LocalSpotsRepository`(지역 파일 위 조회), 포트 `RegionData`· `SpotsRepository`·`SavedSpotsRepository`(기기 로컬, 옮기기 원본)·`RecentSearchesRepository`·`AppPreferences`·`LocationSource`. `account/`: `AuthService`(세션·리프레시·401 재시도), `SavedLibrary`(계정 저장·로그인 요청·옮기기·탈퇴), 포트 `AuthBackend`·`SessionStore`·`AccountApi`·`SavedCache`·`SocialLogin`. Fake들(`fake/`, `AccountFixture`) |
| `:core:data` | 기기 로컬 저장(DataStore + JSON): 기기 저장 스냅샷, 계정 저장 캐시, 최근 검색, S0 노출 여부. 세션은 `EncryptedSessionStore`(EncryptedSharedPreferences) |
| `:core:network` | `HereziApi`(Retrofit), DTO(계약 그대로 snake_case), `toDomain()` 매핑, `RegionSpotsRepository`(지역 파일 ETag/304·디스크 캐시), `SpotDetailApi`(상세), `LiveAccountApi`(`/v1/me/…`), `SupabaseAuthBackend`(OkHttp, Supabase Auth REST) |
| `:core:ui` | DESIGN.md 토큰(`HereziTheme.colors`/`typography`, `HereziSpace`·`Radius`·`Motion` 등, 카테고리 색·아이콘), 공통 컴포넌트(`SpotCard`·배지·버튼·상태 화면·토스트), 이미지 로더(`RemoteImage`·`HereziImageLoader`, 라이브러리 없음), Pretendard 글꼴(`res/font`, OFL 원문 `res/raw`), 한국어 문구·포맷 |
| `:core:map` | `SpotMap` 컴포저블 — 네이버 지도 SDK를 쓰는 **유일한** 모듈. 원형 카테고리 핀·SDK `Clusterer` 묶음·선택 핀(비트맵으로 그림), `PinStyle`로 색·아이콘·글꼴을 받는다. `LocalMapConfig`로 키 유무 전달 |
| `:feature:home` | S1 홈 사진 카드 피드 (`HomeFeedBuilder` 섹션은 `:core:domain` 의 순수 함수) |
| `:feature:nearby` | S1b 전체 목록(`NearbyViewModel`, 시작 필터·페이지·지도 중심 인자) + S2 지도 탭(`MapViewModel`: bbox 조회, 다시 찾기, 내 위치, 하단 시트) |
| `:feature:spotdetail` | S3 상세 + `SpotDetailViewModel`, 네이버지도 길찾기/장소 보기, 공유, 저장 토글 |
| `:feature:onboarding` | S0 위치 권한 안내 |
| `:feature:saved` | S4 저장 목록 (재조회, 끝난 볼거리, 편집·되돌리기) |
| `:feature:search` | S5 검색 (`q`, 최근 검색) |
| `:feature:settings` | S6 설정 (계정·로그아웃·회원 탈퇴, 위치 권한, 정보 출처, 오류 제보) |
| `:feature:auth` | S7 로그인 시트(`LoginSheet`·`LoginViewModel`). :app 뼈대가 `SavedLibrary.loginRequest` 를 보고 띄운다 |
| `:app` | 조립 루트: `HereziApplication`(Hilt), `MainActivity`(Apple 웹 로그인 딥링크), 하단 탭(주변·지도·저장)과 내비게이션, `DataModule`·`AccountModule`(Fake/Live 선택), `AndroidLocationSource`, `auth/`(카카오 SDK·Custom Tabs — 카카오 SDK 는 :app 에서만) |

### 모듈 규칙

`./gradlew checkModuleRules`(= `make check`에 포함)가 강제한다. 규칙을 바꾸려면 루트 `build.gradle.kts`의
`allowedProjectDeps`와 이 문서를 같은 PR에서 고친다.

- `:core:domain`은 프로젝트 의존 0, Android·네트워크 라이브러리 금지.
- feature는 `:core:domain`, `:core:ui`(+ `nearby`만 `:core:map`)에만 의존. **feature끼리 의존 금지** — 화면 연결은 `:app` 내비게이션에서.
- feature는 `:core:network`를 모른다. 데이터는 `SpotsRepository` 포트로만 받는다.
- 네이버 지도 SDK(`com.naver.maps`, `io.github.fornewid`)는 `:core:map`에서만.
- 아무 모듈도 `:app`에 의존하지 않는다.

## 명령

| 명령 | 하는 일 |
|---|---|
| `make check` | `spotlessCheck checkModuleRules detekt testDebugUnitTest assembleDebug` — **PR 전에 반드시 통과**. CI도 이것만 돈다 |
| `make fmt` | ktlint 자동 포맷 (`spotlessApply`) |
| `make test` / `make lint` / `make build` | 부분 실행 |
| `make sync-api` | herezi-api의 `api/openapi.yaml`을 `api/`로 복사 (`../herezi-api` 있으면 거기서, 없으면 GitHub develop에서) |

계측 테스트(에뮬레이터)는 아직 없다. 단위 테스트는 JVM에서 돈다.

## 로컬 설정 (커밋 금지)

`local.properties`(gitignore됨) 또는 같은 이름의 환경 변수:

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
# 선택. 없으면 지도 대신 안내 문구.
NAVER_MAP_CLIENT_ID=...
# 선택. 없으면 FakeSpotsRepository(서버 불필요). 에뮬레이터→로컬 서버: http://10.0.2.2:8080/
HEREZI_API_BASE_URL=
# 선택. 로그인(S7). 없으면 빌드·테스트는 되고 로그인만 실패 문구.
KAKAO_NATIVE_APP_KEY=
SUPABASE_URL=
SUPABASE_PUBLISHABLE_KEY=
```

- 카카오 SDK 는 `https://devrepo.kakao.com/nexus/content/groups/public/` 에서 받는다(그룹 `com.kakao.sdk` 로 제한).
- 카카오 콘솔: Android 플랫폼에 패키지 `com.herezi.app` + **키 해시**(디버그 키, Play 앱 서명 키 둘 다), OpenID Connect 활성화.
- Supabase Auth Redirect URLs 에 `herezi://auth-callback`(Apple 웹 로그인 복귀).

## 위치를 서버로 보내지 않는다 (herezi-api ADR 0004)

앱은 사용자 좌표를 서버로 보내지 않는다. 볼거리는 `GET /v1/regions/seoul/spots`(지역 파일)를 받아 두고 근처·지도·검색·필터·정렬을
기기에서 계산하고, 위치명은 기기 `Geocoder` 만 쓴다. `/v1/spots?lat…`·`/v1/spots/map`·`/v1/geo/reverse` 를 다시 쓰지 않는다
(`NoLocationSendTest` 가 막는다).

## 작업 규칙

1. **기획 먼저, 승인 후 코드.** 새 기능·구조 변경은 이슈에 계획을 적고 승인받은 뒤 시작한다.
   큰 결정은 `docs/adr/NNNN-*.md`로 남긴다.
2. **이슈 → 브랜치 → PR → 머지.** 브랜치 이름 `feat/…`, `fix/…`, `chore/…`. PR 본문에 `Closes #n`.
3. **`develop`(기본 브랜치)으로 PR**, `main`은 출시 전용(`develop` → `main` 릴리스 PR). **main·develop 직접 푸시 금지.** `make check` 로컬 통과 + CI 초록일 때만 squash 머지.
4. **API 계약은 herezi-api가 기준.** 계약을 바꾸고 싶으면 herezi-api에 이슈/PR을 먼저 낸다.
   반영되면 여기서 `make sync-api` → `ContractTest`가 알려주는 대로 `Category`/`When`/DTO를 맞춘다.
   `api/openapi.yaml`을 손으로 고치지 않는다.
5. `free`는 카테고리가 아니라 **필터**(`free=true`)다.
6. 문서·주석은 한국어, 식별자는 영어. 커밋 메시지는 한국어 conventional 스타일(`feat: …`, `chore: …`).
7. 비밀값(`local.properties`, 키, keystore)과 빌드 산출물은 커밋하지 않는다. 파일은 명시적으로 `git add`한다.
8. 테스트: ViewModel은 Fake 저장소로, 매핑·쿼리는 MockWebServer로 단위 테스트한다. 새 로직에는 테스트를 같이 넣는다.

## 에이전트 역할
역할별 지침은 [`agents/`](agents/README.md)에 있다(기획자·디자이너·리뷰어·QA·개발자). 어떤 LLM 도구든 역할을 맡으면 해당 파일(`agents/<역할>.md`)을 먼저 읽고 따른다. Claude Code는 `.claude/agents` 링크로 자동 인식한다.

## 배포 (Play 내부 테스트)
- 업로드 키: 레포 밖 `~/.herezi/android-upload.jks`, 비밀번호 파일 `~/.herezi/android-upload.pass` (절대 커밋·출력 금지, 백업 필요).
- `local.properties`에 `HEREZI_UPLOAD_STORE_FILE`, `HEREZI_UPLOAD_STORE_PASSWORD_FILE`, (선택) `HEREZI_UPLOAD_KEY_ALIAS` 지정. 없으면 릴리스는 서명 없이 빌드된다.
- `HEREZI_VERSION_CODE=N make bundle` → `app/build/outputs/bundle/release/app-release.aab`. 업로드마다 N을 올린다.
- 릴리스는 실서버 API를 쓴다(`prodApiBaseUrl`).

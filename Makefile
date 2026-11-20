# herezi-android 개발 명령. 에이전트와 CI 모두 이 타깃만 쓴다.
GRADLE ?= ./gradlew
GRADLE_FLAGS ?= --no-daemon --console=plain
# 계약 원본. 기본은 옆에 체크아웃한 herezi-api, 없으면 GitHub develop 에서 받는다.
API_REPO_DIR ?= ../herezi-api
API_RAW_URL ?= https://raw.githubusercontent.com/herezi/herezi-api/develop/api/openapi.yaml

.PHONY: check fmt build test lint sync-api clean

## check: 포맷·린트·모듈 규칙·단위 테스트·디버그 빌드 (PR 전에 반드시 통과)
check:
	$(GRADLE) $(GRADLE_FLAGS) spotlessCheck checkModuleRules detekt testDebugUnitTest assembleDebug

## fmt: ktlint 포맷 자동 수정
fmt:
	$(GRADLE) $(GRADLE_FLAGS) spotlessApply

lint:
	$(GRADLE) $(GRADLE_FLAGS) spotlessCheck checkModuleRules detekt

test:
	$(GRADLE) $(GRADLE_FLAGS) testDebugUnitTest

build:
	$(GRADLE) $(GRADLE_FLAGS) assembleDebug

## sync-api: herezi-api 의 api/openapi.yaml 을 api/ 로 복사
sync-api:
	@if [ -f "$(API_REPO_DIR)/api/openapi.yaml" ]; then \
		cp "$(API_REPO_DIR)/api/openapi.yaml" api/openapi.yaml && echo "synced from $(API_REPO_DIR)"; \
	else \
		curl -fsSL "$(API_RAW_URL)" -o api/openapi.yaml && echo "synced from $(API_RAW_URL)"; \
	fi
	@git diff --stat -- api/openapi.yaml

clean:
	$(GRADLE) $(GRADLE_FLAGS) clean

# Feature Specification: Marketplace Verification 경고 제거 및 코루틴 리팩터링

**Feature Branch**: `002-verification-coroutine-refactor`  
**Created**: 2026-04-10  
**Status**: Draft  
**Input**: User description: "JetBrains Marketplace verification 경고 19건(deprecated 13 + experimental 6) 제거 및 비동기 흐름을 코루틴 중심 구조로 전환"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Marketplace Verification 경고 0건 달성 (Priority: P1)

플러그인 개발자가 JSONinja를 JetBrains Marketplace에 제출할 때, Plugin Verifier가 deprecated API 사용 경고 13건과 experimental API 사용 경고 6건을 보고한다. 개발자는 이 경고들을 모두 제거하여 깨끗한 verification 결과를 얻고자 한다.

**Why this priority**: Marketplace verification 경고는 플러그인의 신뢰성과 향후 호환성에 직접적인 영향을 미치며, 향후 플랫폼 버전에서 deprecated API가 제거되면 플러그인이 동작하지 않을 수 있다.

**Independent Test**: Plugin Verifier를 IntelliJ IDEA 2026.1 대상으로 실행하여 deprecated/experimental 경고가 0건인지 확인한다.

**Acceptance Scenarios**:

1. **Given** 현재 소스에 deprecated API 13건이 사용되고 있을 때, **When** 모든 deprecated API를 권장 대체 API로 교체한 후 Plugin Verifier를 실행하면, **Then** deprecated API 관련 경고가 0건이어야 한다.
2. **Given** 현재 소스에 experimental API 6건이 사용되고 있을 때, **When** Kotlin 바이트코드 브릿지 경고를 해결한 후 Plugin Verifier를 실행하면, **Then** experimental API 관련 경고가 0건이어야 한다.
3. **Given** 경고가 모두 제거된 상태에서, **When** 플러그인의 기존 기능(JSON 편집, Diff, 쿼리, 타입 변환 등)을 사용하면, **Then** 기존 기능이 동일하게 동작해야 한다.

---

### User Story 2 - 코루틴 기반 비동기 흐름 전환 (Priority: P2)

플러그인 개발자가 현재 `executeOnPooledThread` + `invokeLater` 패턴으로 구현된 비동기 흐름을 IntelliJ 플랫폼의 코루틴 API 기반으로 전환하여, 코드 유지보수성을 높이고 플랫폼 권장 패턴을 따르고자 한다.

**Why this priority**: 코루틴 전환은 코드 품질과 유지보수성 개선에 중요하지만, verification 경고 제거보다 우선순위가 낮다. 다만 일부 deprecated API 교체(ReadAction 등)가 코루틴 전환과 겹치므로 함께 진행하는 것이 효율적이다.

**Independent Test**: 모든 `executeOnPooledThread` 호출이 서비스 `CoroutineScope` 또는 로컬 Job으로 전환되었는지 코드 검색으로 확인하고, 해당 기능(JSON 쿼리 실행, 랜덤 JSON 생성, 스키마 로드, API 로드 등)이 정상 동작하는지 수동 테스트한다.

**Acceptance Scenarios**:

1. **Given** `executeOnPooledThread`가 7곳에서 사용되고 있을 때, **When** 모든 사용처를 서비스 스코프 또는 로컬 Job 기반으로 전환하면, **Then** 소스에서 `executeOnPooledThread` 호출이 0건이어야 한다.
2. **Given** `invokeLater`가 12개 이상 파일에서 사용되고 있을 때, **When** 코루틴 컨텍스트로 전환 가능한 사용처를 `withContext(Dispatchers.EDT)`로 교체하면, **Then** 불필요한 `invokeLater` 사용이 제거되어야 한다.
3. **Given** `Alarm` 기반 디바운스가 3곳에서 사용되고 있을 때, **When** 로컬 Job + `delay` 패턴으로 전환하면, **Then** 동일한 디바운스 동작이 유지되어야 한다.

---

### User Story 3 - 서비스 수명주기 기반 리소스 관리 (Priority: P3)

플러그인 개발자가 장수명 비동기 작업의 소유권을 `@Service` + `CoroutineScope` 주입 패턴으로 이동하여, 프로젝트 종료 시 자동 취소 및 리소스 정리가 보장되도록 한다.

**Why this priority**: 서비스 수명주기 관리는 P2 코루틴 전환의 자연스러운 결과이며, 플러그인 안정성과 리소스 누수 방지에 기여한다.

**Independent Test**: 서비스 스코프로 이동한 비동기 작업이 프로젝트 닫기/플러그인 언로드 시 정상 취소되는지 확인한다.

**Acceptance Scenarios**:

1. **Given** JSON 쿼리, 랜덤 JSON 생성, 스키마 로드 등의 비동기 작업이 서비스 스코프에서 실행될 때, **When** 프로젝트를 닫으면, **Then** 진행 중인 비동기 작업이 자동으로 취소되어야 한다.
2. **Given** Document 리스너가 parentDisposable 기반으로 등록되어 있을 때, **When** 에디터가 닫히면, **Then** 리스너가 자동으로 해제되어야 한다.

---

### Edge Cases

- Kotlin 바이트코드 브릿지 경고가 `plugin.xml` 선언형 속성 추가만으로 해결되지 않는 경우, 빈 오버라이드를 추가하여 deprecated/experimental super 호출을 방지해야 한다.
- `readAction { }` 전환 시 Write 도착에 의한 재시작이 빈번해지면 성능 저하가 발생할 수 있으므로, 읽기 블록은 짧고 멱등적이어야 한다.
- `DynamicPluginListener` 구현 제거 후 동적 언로드가 정상 동작하는지 확인해야 한다.
- `CancellationException` 또는 `ProcessCanceledException`이 catch-all로 삼켜지지 않도록 주의해야 한다.
- 서비스 생성자에서 다른 서비스를 파라미터로 주입받지 않아야 한다 (deprecated). 호출 시점에 `getService()`로 얻는다.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `ReadAction.compute(ThrowableComputable)` 호출 2건을 `readAction { }` 또는 `readActionBlocking { }`으로 교체해야 한다.
- **FR-002**: `ReadAction.run(ThrowableRunnable)` 호출 1건을 `readActionBlocking { }`으로 교체해야 한다.
- **FR-003**: `Document.addDocumentListener(DocumentListener)` 호출 2건을 `addDocumentListener(listener, parentDisposable)` 형태로 전환하고, 수동 `removeDocumentListener` 호출을 제거해야 한다.
- **FR-004**: `ObjectNode.fields()` 호출 1건을 `ObjectNode.properties()`로 교체해야 한다.
- **FR-005**: `URL(String)` 생성자 호출 1건을 `URI.create(...).toURL()`로 교체해야 한다.
- **FR-006**: `JsonHelperActivationListener`에서 `DynamicPluginListener` 인터페이스 구현과 `plugin.xml` 등록을 제거해야 한다.
- **FR-007**: `JsoninjaToolWindowFactory`의 Kotlin 바이트코드 브릿지 경고 10건을 `plugin.xml` 선언형 속성 추가 및 필요시 빈 오버라이드로 해결해야 한다.
- **FR-008**: `executeOnPooledThread` 사용 7곳을 서비스 `CoroutineScope` 또는 로컬 Job 기반으로 전환해야 한다.
- **FR-009**: 불필요한 `invokeLater` 호출을 `withContext(Dispatchers.EDT)`로 교체해야 한다.
- **FR-010**: `Alarm` 기반 디바운스 3곳을 로컬 Job + `delay` 패턴으로 전환해야 한다.
- **FR-011**: 장수명 비동기 작업은 `@Service` + `CoroutineScope` 주입 패턴으로 소유권을 이동해야 한다.
- **FR-012**: 새로운 비동기 코드에서 `GlobalScope`, `Application.getCoroutineScope()`, `project.coroutineScope`를 사용하지 않아야 한다.
- **FR-013**: 모든 EDT 전환은 `Dispatchers.EDT`를 사용하며, `Dispatchers.Main`은 사용하지 않아야 한다.

### Key Entities

- **Plugin Verifier 경고**: Marketplace가 보고하는 deprecated/experimental API 사용 경고. 타입(deprecated method, deprecated constructor, experimental method)과 발생 위치(파일, 메서드)로 식별된다.
- **비동기 흐름 패턴**: `executeOnPooledThread` + `invokeLater`, `Alarm` 기반 디바운스, 서비스 스코프 코루틴 등 비동기 작업 실행 방식.
- **서비스 스코프**: IntelliJ 플랫폼이 `@Service`에 주입하는 `CoroutineScope`로, 프로젝트/앱 수명주기와 연동된 자동 취소를 제공한다.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Plugin Verifier 실행 시 deprecated API 경고 0건, experimental API 경고 0건 달성
- **SC-002**: 소스 코드에서 `executeOnPooledThread` 호출이 0건
- **SC-003**: 소스 코드에서 `Alarm` 인스턴스 생성이 0건
- **SC-004**: 기존 플러그인 기능(JSON 편집, Diff, 쿼리, 타입 변환, 온보딩 등)이 리팩터링 전과 동일하게 동작
- **SC-005**: 프로젝트 닫기 시 진행 중인 비동기 작업이 자동 취소됨
- **SC-006**: 플러그인 동적 언로드/로드가 정상 동작

## Assumptions

- 현재 소스 기준 최소 지원 버전은 `243` (IntelliJ 2024.3)이며, `2024.1+` 코루틴 API(`Dispatchers.EDT`, `readAction { }`, `readActionBlocking { }`, `smartReadAction`, `withBackgroundProgress`)를 기본으로 사용할 수 있다.
- v1.11.2 태그와 현재 HEAD 소스가 동일하므로 모든 경고는 현재 소스에서 직접 수정 가능하다.
- `plugin.xml`에 선언형 속성을 추가하는 것만으로 일부 바이트코드 브릿지 경고가 해결될 수 있으나, 최종 검증은 Plugin Verifier 재실행으로 확인해야 한다.
- `DynamicPluginListener`의 메서드를 하나도 사용하지 않으므로 구현 제거가 안전하다. 향후 동적 언로드를 막아야 할 경우 `DynamicPluginVetoer`를 별도 구현한다.
- `SwingUtilities.invokeLater`로 사용되는 Swing 컴포넌트 포커스 용도의 호출은 코루틴 전환 대상에서 제외한다.
- 서비스 생성자에서 다른 서비스를 파라미터로 주입받지 않으며, 호출 시점에 `getService()`로 얻는다.

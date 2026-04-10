# Quickstart: Marketplace Verification 경고 제거 및 코루틴 리팩터링

**Feature**: 002-verification-coroutine-refactor

## Prerequisites

- IntelliJ IDEA (2024.3+) with Kotlin plugin
- JDK 17+
- Gradle 8.14.3

## Build & Test

```bash
# Build
./gradlew build

# Run Plugin Verifier
./gradlew runPluginVerifier

# Run tests
./gradlew test

# Run IDE with plugin
./gradlew runIde
```

## Key Files to Modify

### Phase 1: Deprecated/Experimental 경고 제거

1. **단순 교체** (5분 이내):
   - `src/.../typeConversion/JsonToTypeInferenceContext.kt:157` — `fields()` → `properties()`
   - `src/.../jsonQuery/JsonQueryView.kt:72` — `URL(link)` → `URI.create(link).toURL()`

2. **ReadAction 교체** (파일당 10분):
   - `src/.../editor/JsonDocumentFactory.kt:62,76`
   - `src/.../editor/JsonEditorTooltipListener.kt:60`
   - `src/.../editor/FoldingAwareEditorTextField.kt:50`

3. **DocumentListener parentDisposable 추가** (파일당 10분):
   - `src/.../diff/JsonDiffExtension.kt:314`
   - `src/.../convertType/CodeInputPanel.kt:79`

4. **DynamicPluginListener 제거** (5분):
   - `src/.../listeners/JsonHelperActivationListener.kt`
   - `src/main/resources/META-INF/plugin.xml:40-41`

5. **ToolWindow 바이트코드 브릿지** (30분, 검증 포함):
   - `src/main/resources/META-INF/plugin.xml:16-17`
   - `src/.../toolWindow/JsoninjaToolWindowFactory.kt`

### Phase 2: 코루틴 전환

- `executeOnPooledThread` 7곳 → 서비스 스코프 / 로컬 Job
- `Alarm` 3곳 → 로컬 Job + delay
- `invokeLater` ~30회 → `withContext(Dispatchers.EDT)` 또는 서비스 스코프

## Verification

```bash
# Plugin Verifier로 경고 확인
./gradlew runPluginVerifier

# deprecated/experimental 경고 0건 확인
# 결과에서 "deprecated" 또는 "experimental" 검색
```

## IntelliJ 코루틴 API 참고

```kotlin
// 서비스에 CoroutineScope 주입
@Service(Service.Level.PROJECT)
class MyService(private val project: Project, private val cs: CoroutineScope) {
    fun doWork() {
        cs.launch {
            val data = withContext(Dispatchers.IO) { /* IO 작업 */ }
            val psi = readAction { /* PSI 읽기 */ }
            withContext(Dispatchers.EDT) { /* UI 업데이트 */ }
        }
    }
}

// 디바운스 패턴 (Alarm 대체)
private var debounceJob: Job? = null
fun onDocumentChanged() {
    debounceJob?.cancel()
    debounceJob = cs.launch {
        delay(300)
        // 실제 작업
    }
}
```

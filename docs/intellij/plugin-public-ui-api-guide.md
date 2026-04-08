# IntelliJ Platform 공개 UI API 가이드

이 문서는 IntelliJ Platform 기반 외부 플러그인에서 공통적으로 구현하는 UI 확장 중 `tool window`, `action`, `shortcut`, `listener`를 공개 API 기준으로 정리한 가이드입니다.

범위는 의도적으로 좁게 잡았습니다. `plugin.xml`의 공개 등록 방식, `com.intellij.openapi.*`와 `com.intellij.util.messages.*` 아래의 공개 타입, 외부 플러그인이 그대로 따라 할 수 있는 패턴만 다룹니다. PSI 내부 전용 구현, `impl` 내부 클래스, `@ApiStatus.Internal`, `internal="true"` 예시는 제외합니다.

## 1. 공개 API 판별 기준

이 문서에서 공개 API라고 보는 기준은 아래와 같습니다.

- `plugin.xml`에서 외부 플러그인이 직접 등록할 수 있는 공개 태그와 확장점
- `platform-api`, `editor-ui-api`, `extensions`, `projectModel-api`, `ide-core` 쪽의 공개 타입
- 실제 IntelliJ Platform 계열 플러그인에서 널리 쓰는 패턴

이 문서에서 의도적으로 제외하는 것은 아래와 같습니다.

- `impl` 패키지 내부 동작 분석
- PSI 내부 리스너나 PSI 변경 추적처럼 언어 내부 구현에 가까운 내용
- 폐기 예정 메서드를 새 코드의 기본 권장안으로 쓰는 방식

실무에서는 “공개 타입이 존재한다”와 “지금도 권장된다”를 구분해야 합니다. 예를 들어 `ToolWindowManager.registerToolWindow()`는 공개 메서드이지만, 현재 선언 자체에 `ToolWindowFactory`와 `com.intellij.toolWindow` 확장점을 쓰라고 명시되어 있으므로 기본 패턴으로는 권장하지 않습니다.

## 2. Tool Window

### 2.1 언제 쓰는가

`tool window`는 프로젝트 수명주기 동안 비교적 오래 살아 있는 별도 작업 영역이 필요할 때 씁니다.

- 플러그인의 메인 화면이 사이드 패널이나 하단 패널로 계속 노출되어야 할 때
- 여러 탭이나 콘텐츠를 담는 전용 UI를 제공할 때
- 에디터 안의 일회성 팝업이 아니라 IDE 레이아웃에 편입되는 기능이 필요할 때

반대로 짧은 명령 실행, 팝업 메뉴, 컨텍스트 조작은 보통 `action`으로 시작하는 편이 더 자연스럽습니다.

### 2.2 기본 등록 방식

툴 윈도우의 기본 등록 방식은 `plugin.xml`의 선언형 등록입니다. 공개 확장점은 `com.intellij.toolWindow`이고, 실제 확장점 선언은 `ToolWindowEP`를 사용합니다.

```xml
<idea-plugin>
  <extensions defaultExtensionNs="com.intellij">
    <toolWindow
      id="My Tool Window"
      anchor="right"
      factoryClass="com.example.myplugin.MyToolWindowFactory"
      icon="icons/myToolWindow.svg"
      canCloseContents="true"
      doNotActivateOnStart="true"
      secondary="false"/>
  </extensions>
</idea-plugin>
```

핵심 속성은 아래처럼 읽으면 됩니다.

| 속성 | 의미 | 언제 조정하는가 |
| --- | --- | --- |
| `id` | 툴 윈도우 식별자 | 조회, 활성화, 상태 저장에 쓸 이름이 필요할 때 |
| `anchor` | 기본 배치 위치 | 왼쪽, 오른쪽, 아래 중 기본 위치를 정할 때 |
| `factoryClass` | 콘텐츠를 만드는 `ToolWindowFactory` 구현 | 필수 |
| `icon` | 스트라이프 버튼 아이콘 | 툴 윈도우 아이콘을 지정할 때 |
| `canCloseContents` | 내부 탭 콘텐츠를 닫을 수 있는지 | 여러 콘텐츠 탭을 개별적으로 닫게 할 때 |
| `doNotActivateOnStart` | 시작 시 자동 활성화 금지 | 버튼만 보이고 포커스는 뺏지 않게 할 때 |
| `secondary` | 보조 툴 윈도우인지 | 주 툴 윈도우보다 덜 핵심적인 패널일 때 |

`ToolWindowEP` 주석 기준으로 툴 윈도우 아이콘은 13x13 크기 규칙을 따릅니다.

### 2.3 `ToolWindowFactory`에서 해야 할 일

툴 윈도우 구현의 중심은 `ToolWindowFactory`입니다.

```kotlin
class MyToolWindowFactory : ToolWindowFactory {
  override suspend fun isApplicableAsync(project: Project): Boolean {
    return !project.isDefault
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(true)
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val panel = MyToolWindowPanel(project)
    val content = ContentFactory.getInstance()
      .createContent(panel, "", false)
    toolWindow.contentManager.addContent(content)
  }

  override fun shouldBeAvailable(project: Project): Boolean {
    return true
  }
}
```

메서드별 역할은 아래처럼 구분하면 안전합니다.

- `isApplicableAsync(project)`: 이 프로젝트에서 아예 이 팩토리를 활성화할지 결정합니다. 한 번 비활성화되면 다시 되돌리는 용도가 아니므로 일시 상태 토글에는 쓰지 않습니다.
- `init(toolWindow)`: 콘텐츠 생성 전의 추가 초기화가 필요할 때 씁니다.
- `createToolWindowContent(project, toolWindow)`: 실제 UI를 만들고 `contentManager`에 넣는 본체입니다.
- `shouldBeAvailable(project)`: 툴 윈도우가 사용 가능 상태로 보여야 하는지 제어합니다.

`createToolWindowContent()`는 실제 UI 구성 전용으로 두고, 등록 여부 자체는 `isApplicableAsync()`나 `shouldBeAvailable()`로 나누는 편이 코드가 안정적입니다.

### 2.4 런타임에서 쓰는 공개 타입

등록이 끝난 뒤에는 `ToolWindowManager`로 조회하고 활성화하는 패턴이 가장 흔합니다.

```kotlin
val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("My Tool Window")
toolWindow?.activate(null)
```

공개 API 관점에서 기억할 점은 아래와 같습니다.

- `ToolWindowManager.getInstance(project).getToolWindow(id)`로 조회합니다.
- 선언형으로 등록한 툴 윈도우를 나중에 열거나 포커스를 주는 용도로 씁니다.
- 직접 등록 API인 `registerToolWindow(...)`도 공개되어 있지만, 기존 선언에 이미 `ToolWindowFactory`와 확장점 사용이 권장되어 있으므로 기본 선택지로 보지 않는 편이 맞습니다.

### 2.5 권장 패턴

- 기본 등록은 항상 `plugin.xml`의 `<toolWindow>`로 시작합니다.
- 생성 로직은 `ToolWindowFactory`에 모으고, 상태 조건과 UI 생성 책임을 분리합니다.
- 툴 윈도우를 다른 기능에서 열어야 하면 `ToolWindowManager` 조회와 활성화만 사용합니다.
- 툴 윈도우 존재 여부를 자주 바꿔야 한다면 등록과 가용성 문제를 먼저 분리해서 생각합니다.

### 2.6 피해야 할 것

- 새 플러그인에서 `ToolWindowManager.registerToolWindow()`를 기본 구현 방식으로 쓰는 것
- `createToolWindowContent()`에서 등록 여부 판단까지 모두 처리하는 것
- 일시적인 버튼, 한 번성 작업, 짧은 명령까지 모두 툴 윈도우로 올리는 것

## 3. Action

### 3.1 언제 쓰는가

`action`은 사용자가 메뉴, 툴바, 팝업, 검색, 단축키로 실행할 수 있는 명령 단위입니다.

- 메뉴 항목을 추가할 때
- 툴바 버튼을 추가할 때
- 컨텍스트 메뉴 동작을 넣을 때
- 단축키로 바로 실행할 명령을 만들 때

플러그인 UI에서 가장 기본이 되는 진입점은 대개 `tool window`가 아니라 `action`입니다.

### 3.2 기본 등록 방식

사용자에게 보이는 액션은 보통 `plugin.xml`의 `<actions>` 아래에서 등록합니다.

```xml
<idea-plugin>
  <actions>
    <action id="MyPlugin.Refresh"
            class="com.example.myplugin.MyRefreshAction"
            icon="AllIcons.Actions.Refresh">
      <add-to-group group-id="ToolsMenu" anchor="last"/>
    </action>

    <group id="MyPlugin.Group" popup="true">
      <reference ref="MyPlugin.Refresh"/>
      <add-to-group group-id="EditorPopupMenu" anchor="last"/>
    </group>
  </actions>
</idea-plugin>
```

태그별 역할은 아래처럼 보면 됩니다.

| 태그 | 의미 |
| --- | --- |
| `<action>` | 실제 실행 가능한 액션 정의 |
| `<group>` | 액션 묶음 또는 팝업 그룹 |
| `<add-to-group>` | 기존 메뉴나 툴바 그룹 안에 배치 |
| `<reference>` | 이미 정의한 액션이나 그룹 재사용 |

실제 IntelliJ Platform 플러그인에서도 액션 정의와 재배치는 이 네 가지 조합으로 대부분 해결합니다.

### 3.3 `AnAction` 구현 방식

액션 구현의 기본 타입은 `AnAction`이고, 인덱스가 준비되지 않은 상태에서도 동작해야 하면 `DumbAwareAction`을 씁니다.

```kotlin
class MyRefreshAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    refreshSomething(project)
  }
}
```

각 메서드의 역할은 명확히 나누는 것이 좋습니다.

- `update(e)`: 현재 컨텍스트에서 보일지, 활성화할지 계산합니다.
- `actionPerformed(e)`: 실제 동작을 실행합니다.
- `getActionUpdateThread()`: `update()`를 어느 스레드에서 돌릴지 정합니다.

`AnAction` 문서 기준으로 `update()`는 자주 호출되며, 빠르고 부작용이 없어야 합니다. 무거운 작업이나 UI 변경은 `actionPerformed()` 쪽으로 밀어두는 편이 안전합니다. 새 액션은 보통 `ActionUpdateThread.BGT`를 명시하는 편이 자연스럽습니다.

### 3.4 `ActionManager`는 언제 쓰는가

`ActionManager`는 이미 등록된 액션을 조회하거나, 액션 그룹으로 툴바와 팝업을 만들 때 주로 씁니다.

```kotlin
val action = ActionManager.getInstance().getAction("MyPlugin.Refresh")

val toolbar = ActionManager.getInstance()
  .createActionToolbar("MyPlugin.Toolbar", myGroup, true)
```

런타임 등록 API도 있습니다.

```kotlin
ActionManager.getInstance().registerAction("MyPlugin.Dynamic", action, pluginId)
```

다만 사용자에게 보이는 일반 액션의 기본 등록 수단은 여전히 `plugin.xml`입니다. 런타임 등록이 정말 필요하다면 `pluginId`를 함께 넘겨서 키맵 설정 화면의 `Plugins` 노드 아래에 올바르게 보이게 하는 편이 좋습니다.

### 3.5 권장 패턴

- 사용자 명령은 먼저 `AnAction`로 모델링합니다.
- 인덱싱 중에도 안전하게 돌아야 하면 `DumbAwareAction`을 우선 검토합니다.
- `update()`는 상태 계산만 하고, 실행은 `actionPerformed()`에 둡니다.
- 메뉴, 팝업, 툴바 편입은 `plugin.xml`의 그룹 조합으로 해결합니다.

### 3.6 피해야 할 것

- `update()` 안에서 무거운 계산이나 상태 변경을 하는 것
- 사용자에게 보이는 액션을 이유 없이 런타임 등록으로만 숨겨 두는 것
- 단순 컨텍스트 메뉴 항목인데 별도 툴 윈도우를 여는 구조부터 만드는 것

## 4. Shortcut

### 4.1 먼저 결정할 것

단축키는 먼저 “전역 명령인가, 특정 컴포넌트 안에서만 동작하는가”를 구분해야 합니다.

| 상황 | 권장 방식 |
| --- | --- |
| 키맵 설정에 보여야 하는 전역 명령 | 등록된 `action` + `plugin.xml`의 `<keyboard-shortcut>` |
| 기존 액션의 단축키를 그대로 재사용 | `<action use-shortcut-of=\"...\">` |
| 특정 컴포넌트 안에서만 살아야 하는 로컬 동작 | `registerCustomShortcutSet()` |

### 4.2 전역 단축키는 선언형으로 등록

전역 단축키는 액션에 붙여 선언형으로 등록하는 것이 기본입니다.

```xml
<action id="MyPlugin.Refresh"
        class="com.example.myplugin.MyRefreshAction">
  <keyboard-shortcut keymap="$default" first-keystroke="control alt R"/>
  <keyboard-shortcut keymap="Mac OS X 10.5+" first-keystroke="meta alt R"/>
</action>
```

키맵별 변형이 필요하면 `remove="true"`와 `replace-all="true"`를 같이 씁니다. 이 패턴은 운영체제별 기본 키맵 차이를 조정할 때 자주 사용합니다.

기존 액션의 단축키를 그대로 따를 때는 `use-shortcut-of`가 더 단순합니다.

```xml
<action id="MyPlugin.DeleteLike"
        class="com.example.myplugin.MyDeleteLikeAction"
        use-shortcut-of="$Delete"/>
```

이 방식은 “이 액션도 사실상 삭제 동작 계열이다”처럼 기존 사용자 기대를 따라가야 할 때 유용합니다.

### 4.3 특정 컴포넌트 안에서만 살아야 하면 `registerCustomShortcutSet()`

컴포넌트 내부 전용 단축키는 `AnAction.registerCustomShortcutSet()`으로 붙입니다.

```kotlin
class MyPanel(parentDisposable: Disposable) : JPanel() {
  private val runAction = object : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
      runLocalCommand()
    }
  }

  init {
    runAction.registerCustomShortcutSet(
      CustomShortcutSet(KeyStroke.getKeyStroke("control ENTER")),
      this,
      parentDisposable
    )
  }
}
```

`AnAction` 문서 기준으로 이 단축키는 지정한 컴포넌트가 현재 포커스 컴포넌트의 조상일 때만 활성화됩니다. 즉 전역 단축키가 아니라 특정 UI 영역에 묶이는 단축키입니다.

복합 키 입력이 필요하면 `KeyboardShortcut`을 직접 조합할 수 있습니다.

```kotlin
val commentShortcut = CustomShortcutSet(
  KeyboardShortcut(
    KeyStroke.getKeyStroke("control K"),
    KeyStroke.getKeyStroke("control C")
  )
)
```

여기서 `KeyboardShortcut`은 두 번째 키 입력을 가질 수 있습니다. 반면 `KeyboardShortcut.fromString()`은 단일 키 입력 문자열을 `KeyStroke`로 바꾸는 간단한 헬퍼로 이해하는 편이 안전합니다.

### 4.4 수명주기 규칙

컴포넌트 로컬 단축키는 반드시 수명주기를 명시해야 합니다.

- 가장 안전한 방식은 `registerCustomShortcutSet(..., component, parentDisposable)`를 쓰는 것입니다.
- 수동으로 정리해야 하면 `unregisterCustomShortcutSet(component)`를 호출합니다.
- 전역 명령인데 로컬 단축키로만 숨겨 두면 키맵 설정, 검색, 사용자 기대와 어긋납니다.

### 4.5 권장 패턴

- 전역 명령은 액션과 함께 선언형으로 등록합니다.
- 로컬 상호작용만 필요한 경우에만 `CustomShortcutSet`을 씁니다.
- 키맵별 차이가 있으면 XML에서 명시적으로 관리합니다.

### 4.6 피해야 할 것

- 사용자 설정에 보여야 하는 기능을 로컬 단축키로만 구현하는 것
- 일회성 UI 전용 동작에 전역 단축키를 주는 것
- `parentDisposable` 없이 로컬 단축키를 붙이고 해제를 잊는 것

## 5. Listener

### 5.1 리스너는 두 가지 축으로 생각한다

리스너는 보통 아래 두 방식 중 하나를 씁니다.

- 선언형 등록: `plugin.xml`의 `<applicationListeners>` 또는 `<projectListeners>`
- 코드 구독: `messageBus.connect(...).subscribe(...)`

둘 다 공개 API지만, 선택 기준은 다릅니다.

- 플러그인이 로드되면 자동으로 붙어야 하는 전역 반응이면 선언형 등록이 자연스럽습니다.
- 특정 서비스, 패널, 툴 윈도우 콘텐츠의 수명주기에 맞춰 붙고 떨어져야 하면 코드 구독이 더 적합합니다.

### 5.2 선언형 리스너 등록

```xml
<idea-plugin>
  <applicationListeners>
    <listener class="com.example.myplugin.MyAppActivationListener"
              topic="com.intellij.openapi.application.ApplicationActivationListener"
              activeInHeadlessMode="false"/>
  </applicationListeners>

  <projectListeners>
    <listener class="com.example.myplugin.MyToolWindowListener"
              topic="com.intellij.openapi.wm.ex.ToolWindowManagerListener"/>
  </projectListeners>
</idea-plugin>
```

읽는 방법은 아래와 같습니다.

- `<applicationListeners>`: 애플리케이션 메시지 버스에 붙습니다.
- `<projectListeners>`: 프로젝트 메시지 버스에 붙습니다.
- `topic`: 구독할 리스너 인터페이스의 정규 이름입니다.
- `activeInHeadlessMode`, `activeInTestMode`, `os`: 실행 환경 제한을 줄 때 사용합니다.

이 속성들은 실행 환경별로 로딩 범위를 좁히고 싶을 때 사용합니다.

### 5.3 코드 기반 구독

구독 수명주기를 직접 제어해야 하면 `messageBus`를 씁니다.

```kotlin
project.messageBus.connect(parentDisposable)
  .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
    override fun toolWindowShown(toolWindow: ToolWindow) {
      if (toolWindow.id == "My Tool Window") {
        refreshUi()
      }
    }
  })
```

애플리케이션 레벨 구독도 같은 방식입니다.

```kotlin
ApplicationManager.getApplication().messageBus.connect(parentDisposable)
  .subscribe(AnActionListener.TOPIC, object : AnActionListener {
    override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
      logAction(action, event)
    }
  })
```

이 패턴의 핵심은 `connect(parentDisposable)`입니다. 연결 수명주기를 부모 `Disposable`에 묶어야 정리가 자동으로 됩니다. 특별한 이유가 없으면 `connect()`만 단독으로 호출해 연결을 직접 들고 다니지 않는 편이 낫습니다.

### 5.4 공개 리스너 타입을 어떻게 읽어야 하는가

리스너 인터페이스를 보면 보통 `TOPIC`이 같이 선언되어 있습니다.

- `AnActionListener.TOPIC`: `@Topic.AppLevel`
- `ToolWindowManagerListener.TOPIC`: `@Topic.ProjectLevel`
- `ProjectCloseListener.TOPIC`: `@Topic.AppLevel`
- `ApplicationActivationListener.TOPIC`: `@Topic.AppLevel`

이 정보가 의미하는 것은 아래와 같습니다.

- `AppLevel`: 애플리케이션 메시지 버스에서 다루는 이벤트
- `ProjectLevel`: 프로젝트 메시지 버스에서 다루는 이벤트
- `BroadcastDirection`: 메시지가 자식 버스나 부모 버스로 전파되는지 결정

`Topic.BroadcastDirection`은 공개 타입이므로 직접 정의할 때도 사용할 수 있습니다.

| 방향 | 의미 | 보통 언제 쓰는가 |
| --- | --- | --- |
| `NONE` | 브로드캐스트 없음 | 같은 버스 안에서만 처리할 플러그인 내부 이벤트 |
| `TO_CHILDREN` | 자식 버스로 전파 | 애플리케이션에서 프로젝트 쪽으로 흐르게 할 때 |
| `TO_DIRECT_CHILDREN` | 직접 자식에게만 전파 | 애플리케이션 레벨 발행에서 과도한 수집을 피할 때 |
| `TO_PARENT` | 부모 버스로 전파 | 프로젝트 이벤트를 상위 버스로 올려야 할 때 |

외부 플러그인에서 자체 이벤트를 정의할 때는 대개 `NONE`부터 시작하는 편이 가장 안전합니다. 브로드캐스트는 필요가 분명할 때만 추가하는 것이 좋습니다.

### 5.5 자체 `Topic` 정의와 발행

플러그인 내부 서브시스템끼리 결합을 줄이고 싶으면 공개 메시지 버스 API로 자체 `Topic`을 정의할 수 있습니다.

```kotlin
interface MyPluginStateListener {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<MyPluginStateListener> =
      Topic(MyPluginStateListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun stateChanged()
}
```

구독은 아래처럼 합니다.

```kotlin
project.messageBus.connect(parentDisposable)
  .subscribe(MyPluginStateListener.TOPIC, object : MyPluginStateListener {
    override fun stateChanged() {
      refreshUi()
    }
  })
```

발행은 아래처럼 합니다.

```kotlin
project.messageBus.syncPublisher(MyPluginStateListener.TOPIC).stateChanged()
```

이 패턴은 폴링 대신 이벤트로 UI를 갱신하고 싶을 때 유용합니다.

### 5.6 폐기 예정 API와 권장 대체

`ProjectManagerListener.projectOpened()`는 현재 폐기 예정이고, 선언에도 프로젝트 시작 직후에는 `post-startup activity`를 고려하라고 적혀 있습니다. 따라서 새 코드의 기본 권장안으로는 쓰지 않는 편이 맞습니다.

프로젝트 종료 계열은 `ProjectCloseListener`를 기준으로 잡는 편이 더 명확합니다.

- 프로젝트 오픈 직후 초기화가 필요하면 `post-startup activity` 계열을 우선 검토
- 프로젝트 종료 반응이 필요하면 `ProjectCloseListener`
- 툴 윈도우 상태 반응이 필요하면 `ToolWindowManagerListener`
- 액션 실행 추적이 필요하면 `AnActionListener`

### 5.7 권장 패턴

- 가능한 한 이미 제공되는 공개 리스너 토픽을 먼저 찾습니다.
- 수명주기가 분명한 객체에서는 `messageBus.connect(parentDisposable)`를 기본으로 씁니다.
- 선언형 리스너는 플러그인 로딩 직후 자동 연결되어야 하는 경우에만 씁니다.
- 자체 `Topic`은 플러그인 내부 이벤트를 분리할 때만 추가합니다.

### 5.8 피해야 할 것

- `ProjectManagerListener.projectOpened()`를 새 코드의 기본 진입점으로 쓰는 것
- `connect()`만 호출하고 연결 해제를 잊는 것
- 내부 `impl` 리스너나 내부 토픽 정규 이름을 그대로 문서화하거나 의존하는 것
- 프로젝트 상태를 다루는 이벤트를 애플리케이션 레벨에 무조건 올려 버리는 것

## 6. 빠른 선택표

| 요구사항 | 권장 수단 | 등록 위치 | 키맵 노출 |
| --- | --- | --- | --- |
| IDE 레이아웃에 고정된 전용 패널 | `toolWindow` + `ToolWindowFactory` | `<extensions>` | 해당 없음 |
| 메뉴, 툴바, 팝업에서 실행할 명령 | `action` + `AnAction` | `<actions>` | 필요 시 가능 |
| 사용자가 키맵에서 재설정할 전역 단축키 | `action` + `<keyboard-shortcut>` | `<actions>` | 예 |
| 특정 패널 안에서만 동작할 로컬 단축키 | `registerCustomShortcutSet()` | 코드 | 아니오 |
| 플랫폼 이벤트에 반응하는 전역 로직 | `<applicationListeners>` 또는 `<projectListeners>` | `plugin.xml` | 해당 없음 |
| 특정 서비스나 UI의 수명주기에 묶인 이벤트 반응 | `messageBus.connect(...).subscribe(...)` | 코드 | 해당 없음 |
| 플러그인 내부 이벤트 팬아웃 | `Topic` + `messageBus.syncPublisher()` | 코드 | 해당 없음 |

## 7. 구현 체크리스트

- 이 기능은 메뉴, 툴바, 검색, 단축키에서 실행되는 명령인가
- 이 기능은 IDE 레이아웃에 붙는 지속적인 패널인가
- 이 단축키는 키맵 설정에서 보여야 하는가
- 이 단축키는 특정 컴포넌트 안에서만 살아야 하는가
- 이 리스너는 애플리케이션 범위인가, 프로젝트 범위인가
- 수명주기를 `Disposable`에 묶었는가
- 이미 있는 공개 리스너 토픽으로 해결 가능한가
- 폐기 예정 메서드를 기본 진입점으로 쓰고 있지 않은가
- 내부 `impl` 타입에 의존하고 있지 않은가

## 8. 외부 저장소에서 참고할 최소 검색 키워드

이 문서는 IntelliJ Community 저장소가 없어도 읽고 구현할 수 있도록 작성했습니다. 그래도 구현 중에 공식 문서나 IDE 자동완성으로 추가 확인이 필요하면 아래 이름만 검색하면 됩니다.

### 8.1 Tool Window

- `plugin.xml <toolWindow>`
- `ToolWindowFactory`
- `ToolWindow`
- `ToolWindowManager`

### 8.2 Action

- `plugin.xml <actions>`
- `plugin.xml <action>`
- `plugin.xml <group>`
- `plugin.xml <add-to-group>`
- `plugin.xml <reference>`
- `AnAction`
- `DumbAwareAction`
- `AnActionEvent`
- `ActionManager`
- `ActionUpdateThread`

### 8.3 Shortcut

- `plugin.xml <keyboard-shortcut>`
- `use-shortcut-of`
- `CustomShortcutSet`
- `KeyboardShortcut`
- `registerCustomShortcutSet`

### 8.4 Listener

- `plugin.xml <applicationListeners>`
- `plugin.xml <projectListeners>`
- `MessageBus`
- `Topic`
- `AnActionListener`
- `ToolWindowManagerListener`
- `ProjectCloseListener`
- `ApplicationActivationListener`

### 8.5 공식 문서에서 찾을 때의 주제 이름

- `Tool Windows`
- `Action System`
- `Plugin Configuration File`
- `Messaging Infrastructure`

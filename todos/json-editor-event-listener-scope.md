# TODO: JsonEditor 이벤트 리스너 범위 제한

요구 사항: JSON 에디터의 DocumentListener가 전역 편집기에 남지 않도록 플러그인 영역으로만 제한해야 한다. 현재 구현은 `JsonEditor`가 `Disposable`을 구현하고 있음에도 `JsonHelperTabbedPane`에서 생성된 에디터를 부모 `Disposable`에 연결하지 않아 탭을 닫거나 툴 윈도우를 닫아도 리스너가 해제되지 않는다.

- [ ] `JsonHelperTabbedPane.createEditor()`/`addNewTabInternal()`(src/main/kotlin/com/livteam/jsoninja/ui/component/JsonHelperTabbedPane.kt:197-249)에서 새 `JsonEditor`를 만들 때 `Disposer.register(tabContentPanel, editor)` 혹은 ToolWindow 생명주기를 따르는 상위 Disposable에 반드시 등록해 DocumentListener가 자동 해제되도록 한다.
- [ ] 탭 닫기 로직(`closeCurrentTab()` 및 `TabCloseButtonListener`, 같은 파일 124-208, 347-380)에서 `removeTabAt()` 전에 해당 탭이 보유한 `JsonEditor`와 `JmesPathComponent`를 명시적으로 `Disposer.dispose`하여 리스너가 전역 문서에 남지 않도록 한다.
- [ ] `JsoninjaToolWindowFactory.createToolWindowContent()`(src/main/kotlin/com/livteam/jsoninja/ui/toolWindow/JsoninjaToolWindowFactory.kt:11-30)에서 ToolWindow content가 disposed 될 때 `JsonHelperPanel` 하위 컴포넌트까지 함께 dispose되는지 확인하고, 필요 시 `Disposer.register(toolWindow.disposable, jsonHelperPanel)`을 추가한다.
- [ ] `JsonEditor.setupClipboardMonitoring()`과 `setupContentChangeListener()`(src/main/kotlin/com/livteam/jsoninja/ui/component/JsonEditor.kt:120-197)에서 `Disposer.register(this)`로만 정리하는 현 구조가 상위 Disposable과 연결되지 않는 문제를 해결했는지 수동으로 검증한다. 등록 계층이 끊겨 있다면 listener가 IDE 전역 Document 변경을 계속 수신하므로, Disposable 체인이 제대로 연결됐는지 테스트한다.

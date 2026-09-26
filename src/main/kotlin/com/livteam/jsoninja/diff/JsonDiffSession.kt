package com.livteam.jsoninja.diff

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.livteam.jsoninja.LocalizationBundle
import com.livteam.jsoninja.model.JsonDiffAlignmentOptions
import com.livteam.jsoninja.model.JsonDiffSortMode
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * 하나의 JSON Diff 비교 세션.
 * editor tab/window host가 다시 만들어져도 같은 Document 쌍과 정렬 선택, 작업 세대, 정렬 되돌리기 스냅샷,
 * 양쪽 이름을 유지합니다. 이 값들은 현재 비교에만 속하며 설정에 저장하지 않습니다.
 *
 * Threading: 상태 변경은 EDT에서 수행하고, background 작업은 volatile 값과 generation으로 오래된 결과를 거부합니다.
 */
class JsonDiffSession(
    val leftDocument: Document,
    val rightDocument: Document,
    shouldAutoSort: Boolean
) {
    /**
     * 수동 정렬 직전 텍스트와 정렬 직후 Document stamp
     */
    data class RestoreSnapshot(
        val leftText: String,
        val rightText: String,
        val sortedLeftStamp: Long,
        val sortedRightStamp: Long
    )

    private val generation = AtomicLong()
    private val pendingWorkListeners = CopyOnWriteArrayList<() -> Unit>()

    // viewer가 다시 만들어질 때마다 새 라벨이 생기므로 닫힌 viewer의 라벨은 GC가 정리하도록 약하게 보관한다.
    private val titleLabels: MutableMap<JBLabel, Side> = Collections.synchronizedMap(WeakHashMap())

    @Volatile
    private var restoreSnapshot: RestoreSnapshot? = null

    @Volatile
    var isClosed: Boolean = false
        private set

    @Volatile
    var sortMode: JsonDiffSortMode = JsonDiffSortMode.KEY_ASCENDING
        private set

    @Volatile
    var shouldAutoSort: Boolean = shouldAutoSort
        private set

    @Volatile
    var shouldIgnoreArrayOrder: Boolean = false
        private set

    @Volatile
    private var customLeftTitle: String? = null

    @Volatile
    private var customRightTitle: String? = null

    val alignmentOptions: JsonDiffAlignmentOptions
        get() = JsonDiffAlignmentOptions(sortMode, shouldIgnoreArrayOrder)

    val currentGeneration: Long
        get() = generation.get()

    fun isCurrent(expectedGeneration: Long): Boolean {
        return !isClosed && generation.get() == expectedGeneration
    }

    fun changeSortMode(newSortMode: JsonDiffSortMode) {
        sortMode = newSortMode
        invalidatePendingWork()
    }

    fun changeAutoSort(isEnabled: Boolean) {
        shouldAutoSort = isEnabled
        invalidatePendingWork()
    }

    fun changeIgnoreArrayOrder(isEnabled: Boolean) {
        shouldIgnoreArrayOrder = isEnabled
        invalidatePendingWork()
    }

    /**
     * 대기 중이거나 계산 중인 정렬 결과를 무효화하고 새 generation을 반환합니다.
     */
    fun invalidatePendingWork(): Long {
        val nextGeneration = generation.incrementAndGet()
        pendingWorkListeners.forEach { it() }
        return nextGeneration
    }

    fun addPendingWorkListener(parentDisposable: Disposable, listener: () -> Unit) {
        pendingWorkListeners.add(listener)
        Disposer.register(parentDisposable) { pendingWorkListeners.remove(listener) }
    }

    fun replaceRestoreSnapshot(snapshot: RestoreSnapshot) {
        restoreSnapshot = snapshot
    }

    fun clearRestoreSnapshot() {
        restoreSnapshot = null
    }

    /**
     * 정렬 이후 어느 쪽도 바뀌지 않았을 때만 스냅샷을 반환합니다.
     */
    fun getRestorableSnapshot(): RestoreSnapshot? {
        val snapshot = restoreSnapshot ?: return null
        if (isClosed) return null
        if (leftDocument.modificationStamp != snapshot.sortedLeftStamp) return null
        if (rightDocument.modificationStamp != snapshot.sortedRightStamp) return null
        return snapshot
    }

    fun getCustomTitle(side: Side): String? {
        return if (side == Side.LEFT) customLeftTitle else customRightTitle
    }

    fun getDefaultTitle(side: Side): String {
        val messageKey = if (side == Side.LEFT) "dialog.json.diff.left" else "dialog.json.diff.right"
        return LocalizationBundle.message(messageKey)
    }

    fun getTitle(side: Side): String {
        return getCustomTitle(side) ?: getDefaultTitle(side)
    }

    /**
     * 양쪽 이름을 함께 바꾸고 표시 중인 제목을 갱신합니다.
     * 앞뒤 공백을 제거한 빈 이름은 기본 이름으로 돌아갑니다. Document와 정렬 상태는 바꾸지 않습니다.
     */
    fun changeTitles(leftTitle: String?, rightTitle: String?) {
        customLeftTitle = leftTitle?.trim()?.takeIf { it.isNotEmpty() }
        customRightTitle = rightTitle?.trim()?.takeIf { it.isNotEmpty() }

        val visibleLabels = synchronized(titleLabels) { titleLabels.entries.map { it.key to it.value } }
        visibleLabels.forEach { (label, side) -> label.text = getTitle(side) }
    }

    /**
     * diff viewer가 제목을 만들 때 현재 이름을 사용하고, 이후 이름 변경을 같은 라벨에 반영합니다.
     */
    fun createTitleCustomizers(): List<DiffEditorTitleCustomizer> {
        return listOf(Side.LEFT, Side.RIGHT).map { side ->
            DiffEditorTitleCustomizer { createTitleLabel(side) }
        }
    }

    private fun createTitleLabel(side: Side): JBLabel {
        val label = JBLabel(getTitle(side)).setCopyable(true)
        titleLabels[label] = side
        return label
    }

    fun close() {
        isClosed = true
        restoreSnapshot = null
        invalidatePendingWork()
    }
}

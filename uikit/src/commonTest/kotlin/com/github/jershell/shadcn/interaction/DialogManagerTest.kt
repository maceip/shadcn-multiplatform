package com.github.jershell.shadcn.interaction

import com.github.jershell.shadcn.components.dialog.DialogManager
import com.github.jershell.shadcn.components.dialog.DialogSpec
import kotlin.test.*

class DialogManagerTest {
    @Test fun cancellationCanReenterCloseWithoutRepeatingCallback() {
        val manager = DialogManager()
        var calls = 0
        var id = 0L
        id = manager.show(DialogSpec("title", onCancel = { calls++; manager.close(id) }))
        manager.close(id)
        manager.close(id)
        manager.clear()
        assertEquals(1, calls)
    }

    @Test fun clearCanReenterAndDoesNotDropNewDialogsFromCallback() {
        val manager = DialogManager()
        var calls = 0
        manager.show(DialogSpec("title", onCancel = { calls++; manager.clear(); manager.show(DialogSpec("new")) }))
        manager.clear()
        assertEquals(1, calls)
        assertEquals("new", manager.entries.single().spec.title)
    }
}

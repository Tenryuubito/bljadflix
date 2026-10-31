package com.shadow.firetv.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class FocusContainedRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    override fun focusSearch(focused: View, direction: Int): View? {
        val next = super.focusSearch(focused, direction)
        if (direction != View.FOCUS_UP && direction != View.FOCUS_DOWN) return next
        return if (next != null && isDescendant(next)) next else focused
    }

    private fun isDescendant(view: View): Boolean {
        var parent = view.parent
        while (parent != null) {
            if (parent === this) return true
            parent = parent.parent
        }
        return false
    }
}

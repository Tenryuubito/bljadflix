package com.shadow.firetv.core.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class TabRowRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    var selectedPosition: Int = NO_POSITION

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        if (!hasFocus()) {
            val selected = layoutManager?.findViewByPosition(selectedPosition)
            if (selected != null && selected.isFocusable && selected.visibility == View.VISIBLE) {
                views.add(selected)
                return
            }
        }
        super.addFocusables(views, direction, focusableMode)
    }
}

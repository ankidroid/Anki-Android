package com.ichi2.anki.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.TypedValue
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.R

class DeckHierarchyLinesDecoration(
    context: Context,
    private val adapter: DeckAdapter,
) : RecyclerView.ItemDecoration() {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.resources.displayMetrics)

            val typedValue = TypedValue()
            context.theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            color = typedValue.data
            alpha = 30
        }

    private val nestedIndent = context.resources.getDimension(R.dimen.keyline_1)
    private val expanderCenterOffset = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics)
    private val cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, context.resources.displayMetrics)

    override fun onDrawOver(
        c: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val childCount = parent.childCount
        if (childCount == 0) return

        val currentList = adapter.currentList
        if (currentList.isEmpty()) return

        for (i in 0 until childCount) {
            val view = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) continue

            val node = currentList[position]
            val depth = node.depth

            val top = view.top.toFloat()
            val bottom = view.bottom.toFloat()
            val centerY = (top + bottom) / 2f

            for (level in 0 until depth - 1) {
                if (hasNextSibling(position, level + 1, currentList)) {
                    val x = getLineX(level)
                    c.drawLine(x, top, x, bottom, paint)
                }
            }

            if (depth > 0) {
                val level = depth - 1
                val x = getLineX(level)

                val hasSibling = hasNextSibling(position, depth, currentList)
                if (hasSibling) {
                    c.drawLine(x, top, x, bottom, paint)
                }

                val path = Path()
                path.moveTo(x, top)

                path.lineTo(x, centerY - cornerRadius)
                path.quadTo(x, centerY, x + cornerRadius, centerY)

                val endX = getLineX(depth) - expanderCenterOffset + cornerRadius
                path.lineTo(endX, centerY)

                c.drawPath(path, paint)
            }

            if (position + 1 < currentList.size && currentList[position + 1].depth == depth + 1) {
                val x = getLineX(depth)
                c.drawLine(x, centerY, x, bottom, paint)
            }
        }
    }

    private fun getLineX(depth: Int): Float = depth * nestedIndent + expanderCenterOffset

    private fun hasNextSibling(
        position: Int,
        targetDepth: Int,
        list: List<com.ichi2.anki.deckpicker.DisplayDeckNode>,
    ): Boolean {
        for (i in position + 1 until list.size) {
            val depth = list[i].depth
            if (depth == targetDepth) return true
            if (depth < targetDepth) return false
        }
        return false
    }
}

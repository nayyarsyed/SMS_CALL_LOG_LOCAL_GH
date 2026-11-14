package com.example.sms_call_log_local_gh

import android.content.Context
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

class FastScrollBar(context: Context) : View(context) {
    var onProgressChanged: ((Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val thumbHeight = 72f * density
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        alpha = 100
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val attributes = context.obtainStyledAttributes(
            intArrayOf(android.R.attr.colorAccent)
        )
        color = attributes.getColor(0, Color.BLUE)
        attributes.recycle()
    }
    private var scrollRange = 0
    private var progress = 0f

    init {
        contentDescription = "Fast scroll list"
        isClickable = true
        isFocusable = true
    }

    fun updatePosition(scrollY: Int, maxScroll: Int) {
        scrollRange = maxScroll.coerceAtLeast(0)
        progress = if (scrollRange == 0) 0f else (scrollY.toFloat() / scrollRange).coerceIn(0f, 1f)
        visibility = if (scrollRange > 0) VISIBLE else GONE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (scrollRange == 0 || height <= thumbHeight) return

        val trackX = width / 2f
        val trackTop = thumbHeight / 2f
        val trackBottom = height - trackTop
        canvas.drawRoundRect(
            RectF(trackX - 2f * density, trackTop, trackX + 2f * density, trackBottom),
            2f * density,
            2f * density,
            trackPaint
        )

        val thumbTop = trackTop + (trackBottom - trackTop) * progress - thumbHeight / 2f
        canvas.drawRoundRect(
            RectF(
                trackX - 14f * density,
                thumbTop,
                trackX + 14f * density,
                thumbTop + thumbHeight
            ),
            14f * density,
            14f * density,
            thumbPaint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (scrollRange == 0 || height <= thumbHeight) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val trackTop = thumbHeight / 2f
                val trackBottom = height - trackTop
                val position = ((event.y - trackTop) / (trackBottom - trackTop)).coerceIn(0f, 1f)
                onProgressChanged?.invoke(position)
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

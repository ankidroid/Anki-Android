package com.ichi2.anki

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom circular progress indicator with enhanced visual design.
 * 
 * Features:
 * - Radial gradient background
 * - Progress arc with rounded caps
 * - Tick marks around the circle
 * - Centered percentage text
 * - Smooth animations
 */
class CustomCircularProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progress = 0
    private var animatedProgress = 0f
    private var maxProgress = 100
    
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    private val rectF = RectF()
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f
    private var strokeWidth = 0f
    
    private var animator: ValueAnimator? = null
    
    // Colors
    private var primaryColor: Int = 0
    private var secondaryColor: Int = 0
    private var backgroundColor: Int = 0
    
    init {
        // Get theme colors
        val typedArray = context.theme.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.colorPrimary,
                android.R.attr.colorControlHighlight,
                android.R.attr.colorBackground
            )
        )
        primaryColor = typedArray.getColor(0, Color.parseColor("#db660d"))
        secondaryColor = typedArray.getColor(1, Color.parseColor("#3F3F3F"))
        backgroundColor = typedArray.getColor(2, Color.parseColor("#1a1717"))
        typedArray.recycle()
        
        setupPaints()
    }
    
    private fun setupPaints() {
        // Track paint (background circle)
        trackPaint.style = Paint.Style.STROKE
        trackPaint.color = secondaryColor
        trackPaint.strokeCap = Paint.Cap.ROUND
        
        // Progress paint (foreground arc)
        progressPaint.style = Paint.Style.STROKE
        progressPaint.color = primaryColor
        progressPaint.strokeCap = Paint.Cap.ROUND
        
        // Tick paint
        tickPaint.style = Paint.Style.STROKE
        tickPaint.strokeCap = Paint.Cap.ROUND
        
        // Text paint
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        centerX = w / 2f
        centerY = h / 2f
        
        val size = min(w, h).toFloat()
        radius = size / 2f * 0.7f
        strokeWidth = size / 25f
        
        trackPaint.strokeWidth = strokeWidth
        progressPaint.strokeWidth = strokeWidth
        tickPaint.strokeWidth = size / 250f
        textPaint.textSize = size / 6f
        
        rectF.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw radial gradient background
        drawGradientBackground(canvas)
        
        // Draw track circle
        canvas.drawCircle(centerX, centerY, radius, trackPaint)
        
        // Draw progress arc
        val sweepAngle = (360f / maxProgress) * animatedProgress
        canvas.drawArc(rectF, -90f, sweepAngle, false, progressPaint)
        
        // Draw tick marks
        drawTickMarks(canvas)
        
        // Draw percentage text
        val text = "${progress}%"
        val textY = centerY - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text, centerX, textY, textPaint)
    }
    
    private fun drawGradientBackground(canvas: Canvas) {
        val gradient = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(
                adjustAlpha(primaryColor, 0.45f),
                adjustAlpha(secondaryColor, 0.15f)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        backgroundPaint.shader = gradient
        canvas.drawCircle(centerX, centerY, radius, backgroundPaint)
    }
    
    private fun drawTickMarks(canvas: Canvas) {
        val tickCount = 12 // Draw 12 tick marks around the circle
        val outerRadius = radius + strokeWidth / 2f
        val gap = 15f
        val tickLength = strokeWidth
        
        for (i in 0 until tickCount) {
            // Calculate which tick marks should be highlighted based on animated progress
            val tickProgressThreshold = (i * maxProgress) / tickCount
            // Use animatedProgress and check if we've passed this tick (works for both directions)
            val alpha = if (animatedProgress >= tickProgressThreshold) 1f else 0.3f
            tickPaint.color = adjustAlpha(primaryColor, alpha)
            
            val angleInDegrees = i * 360f / tickCount
            val angleInRad = Math.toRadians(angleInDegrees.toDouble() - 90.0)
            
            val gapAdjustmentX = -sin(Math.toRadians(angleInDegrees.toDouble())) * gap
            val gapAdjustmentY = cos(Math.toRadians(angleInDegrees.toDouble())) * gap
            
            val startX = (outerRadius * cos(angleInRad) + centerX + gapAdjustmentX).toFloat()
            val startY = (outerRadius * sin(angleInRad) + centerY + gapAdjustmentY).toFloat()
            
            val endX = ((outerRadius + tickLength) * cos(angleInRad) + centerX + gapAdjustmentX).toFloat()
            val endY = ((outerRadius + tickLength) * sin(angleInRad) + centerY + gapAdjustmentY).toFloat()
            
            canvas.drawLine(startX, startY, endX, endY, tickPaint)
        }
    }
    
    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }
    
    /**
     * Sets the progress value with optional animation.
     * 
     * @param value Progress value (0 to maxProgress)
     * @param animate Whether to animate the change
     */
    fun setProgress(value: Int, animate: Boolean = true) {
        val newProgress = value.coerceIn(0, maxProgress)
        
        if (progress == newProgress) return
        
        progress = newProgress
        
        animator?.cancel()
        
        if (animate) {
            animator = ValueAnimator.ofFloat(animatedProgress, progress.toFloat()).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                addUpdateListener { animation ->
                    animatedProgress = animation.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            animatedProgress = progress.toFloat()
            invalidate()
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
    
    /**
     * Gets the current progress value.
     */
    fun getProgress(): Int = progress
    
    /**
     * Sets the maximum progress value.
     */
    fun setMaxProgress(max: Int) {
        maxProgress = max.coerceAtLeast(1)
        invalidate()
    }
    
    /**
     * Sets the primary color for the progress indicator.
     */
    fun setPrimaryColor(color: Int) {
        primaryColor = color
        progressPaint.color = color
        invalidate()
    }
    
    /**
     * Sets the secondary color for the track.
     */
    fun setSecondaryColor(color: Int) {
        secondaryColor = color
        trackPaint.color = color
        invalidate()
    }
}

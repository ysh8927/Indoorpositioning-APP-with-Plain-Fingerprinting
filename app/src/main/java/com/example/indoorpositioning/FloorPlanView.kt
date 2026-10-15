package com.example.indoorpositioning

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView

class FloorPlanView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    // 현재 위치 (미터 단위)
    private var locationX: Float? = null
    private var locationY: Float? = null

    // 도면 캘리브레이션 정보
    private var mapInfo: MapInfo? = null

    // 위치 마커 페인트
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    private val markerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000")
        style = Paint.Style.FILL
    }

    fun setMapInfo(info: MapInfo) {
        mapInfo = info
        invalidate()
    }

    fun setLocation(x: Float, y: Float) {
        locationX = x
        locationY = y
        invalidate()
    }

    fun clearLocation() {
        locationX = null
        locationY = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val lx = locationX ?: return
        val ly = locationY ?: return
        val info = mapInfo ?: return

        // 미터 → 픽셀 변환
        val imgW = info.image_width_px?.toFloat() ?: return
        val imgH = info.image_height_px?.toFloat() ?: return
        val realW = info.real_width_m ?: return
        val realH = info.real_height_m ?: return
        val originX = info.origin_x_px ?: 0f
        val originY = info.origin_y_px ?: 0f

        val scaleX = realW / imgW
        val scaleY = realH / imgH

        val pixelX = originX + (lx / scaleX)
        val pixelY = originY + (ly / scaleY)

        // ImageView 내 실제 렌더링 영역 계산
        val imageMatrix = imageMatrix
        val matrixValues = FloatArray(9)
        imageMatrix.getValues(matrixValues)

        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val scaleXView = matrixValues[Matrix.MSCALE_X]
        val scaleYView = matrixValues[Matrix.MSCALE_Y]

        val drawX = transX + pixelX * scaleXView
        val drawY = transY + pixelY * scaleYView

        // 마커 그리기
        val markerRadius = 24f
        canvas.drawCircle(drawX, drawY, markerRadius, markerPaint)
        canvas.drawCircle(drawX, drawY, markerRadius, markerStrokePaint)

        // 위치 텍스트
        val label = "(%.1f, %.1f)m".format(lx, ly)
        val textPadding = 8f
        val textWidth = textPaint.measureText(label)
        val textHeight = textPaint.textSize

        val bgLeft   = drawX - textWidth / 2 - textPadding
        val bgTop    = drawY - markerRadius - textHeight - textPadding * 2 - 4f
        val bgRight  = drawX + textWidth / 2 + textPadding
        val bgBottom = drawY - markerRadius - 4f

        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 8f, 8f, textBgPaint)
        canvas.drawText(label, drawX, bgBottom - textPadding / 2, textPaint)
    }
}

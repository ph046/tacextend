package com.tac.extend

import android.content.Context
import android.graphics.*
import android.view.View

class OverlayView(context: Context) : View(context) {

    private var lines: List<DetectedLine> = emptyList()
    private var pockets: List<Pocket> = emptyList()

    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255,0,255,80); strokeWidth=8f
        style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND
    }
    private val greenGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60,0,255,80); strokeWidth=22f
        style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.argb(220,255,50,50); strokeWidth=6f
        style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND
        pathEffect=DashPathEffect(floatArrayOf(20f,12f),0f)
    }
    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.argb(220,255,255,255); strokeWidth=3f; style=Paint.Style.STROKE
    }
    private val pocketHighlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.argb(160,0,255,80); style=Paint.Style.FILL
    }
    private val pocketRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.argb(220,0,255,80); strokeWidth=3f; style=Paint.Style.STROKE
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color=Color.argb(240,0,255,80); style=Paint.Style.FILL
    }

    fun update(lines: List<DetectedLine>, pockets: List<Pocket>) {
        this.lines=lines; this.pockets=pockets; postInvalidate()
    }

    fun clear() { lines=emptyList(); postInvalidate() }

    override fun onDraw(canvas: Canvas) {
        for (line in lines) {
            if (line.willScore) {
                canvas.drawLine(line.end.x,line.end.y,line.extended.x,line.extended.y,greenGlow)
                canvas.drawLine(line.end.x,line.end.y,line.extended.x,line.extended.y,greenPaint)
                canvas.drawCircle(line.nearestPocket.x,line.nearestPocket.y,22f,pocketHighlight)
                canvas.drawCircle(line.nearestPocket.x,line.nearestPocket.y,22f,pocketRing)
                drawArrow(canvas,line.end.x,line.end.y,line.extended.x,line.extended.y)
            } else {
                canvas.drawLine(line.end.x,line.end.y,line.extended.x,line.extended.y,redPaint)
            }
            canvas.drawCircle(line.start.x,line.start.y,14f,contactPaint)
        }
    }

    private fun drawArrow(canvas: Canvas,x1:Float,y1:Float,x2:Float,y2:Float) {
        val dx=x2-x1;val dy=y2-y1;val len=kotlin.math.hypot(dx,dy)
        if(len<10f) return
        val nx=dx/len;val ny=dy/len;val s=26f
        val path=Path().apply {
            moveTo(x2,y2)
            lineTo(x2-s*nx+s*0.5f*ny,y2-s*ny-s*0.5f*nx)
            lineTo(x2-s*nx-s*0.5f*ny,y2-s*ny+s*0.5f*nx)
            close()
        }
        canvas.drawPath(path,arrowPaint)
    }
}

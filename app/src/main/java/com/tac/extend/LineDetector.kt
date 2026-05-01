package com.tac.extend

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

data class PointF2(val x: Float, val y: Float)
data class Pocket(val x: Float, val y: Float)
data class DetectedLine(
    val start: PointF2,
    val end: PointF2,
    val extended: PointF2,
    val nearestPocket: Pocket,
    val willEnter: Boolean,
    val lineColor: Int
)

object LineDetector {

    private const val SCALE = 0.25f

    fun detect(bmp: Bitmap): Pair<List<DetectedLine>, List<Pocket>> {
        val sw = (bmp.width * SCALE).toInt().coerceAtLeast(1)
        val sh = (bmp.height * SCALE).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(bmp, sw, sh, false)
        val inv = 1f / SCALE
        val pixels = IntArray(sw * sh)
        small.getPixels(pixels, 0, sw, 0, 0, sw, sh)
        small.recycle()

        var minX = sw; var maxX = 0; var minY = sh; var maxY = 0
        for (y in 0 until sh) for (x in 0 until sw) {
            val p = pixels[y * sw + x]
            if (isGreen(Color.red(p), Color.green(p), Color.blue(p))) {
                if (x < minX) minX = x; if (x > maxX) maxX = x
                if (y < minY) minY = y; if (y > maxY) maxY = y
            }
        }
        if (minX >= maxX || minY >= maxY) {
            minX=(sw*0.1f).toInt(); maxX=(sw*0.9f).toInt()
            minY=(sh*0.1f).toInt(); maxY=(sh*0.9f).toInt()
        }

        val sx1=minX*inv; val sx2=maxX*inv
        val sy1=minY*inv; val sy2=maxY*inv; val mx=(sx1+sx2)/2f
        val pockets = listOf(
            Pocket(sx1+24f,sy1+24f), Pocket(mx,sy1+12f), Pocket(sx2-24f,sy1+24f),
            Pocket(sx1+24f,sy2-24f), Pocket(mx,sy2-12f), Pocket(sx2-24f,sy2-24f)
        )

        val linePixels = mutableListOf<Triple<Int,Int,Int>>()
        for (y in minY..maxY) for (x in minX..maxX) {
            val p = pixels[y*sw+x]
            val r=Color.red(p); val g=Color.green(p); val b=Color.blue(p)
            if (isLinePixel(r,g,b)) linePixels.add(Triple(x,y,p))
        }

        if (linePixels.size < 5) return Pair(emptyList(), pockets)

        val lines = findLines(linePixels, inv, pockets)
        return Pair(lines, pockets)
    }

    private fun isGreen(r: Int, g: Int, b: Int) = g > 60 && g > r+15 && g > b+15

    private fun isLinePixel(r: Int, g: Int, b: Int): Boolean {
        if (isGreen(r,g,b)) return false
        val brightness = (r+g+b)/3
        if (brightness < 80) return false
        val mx=maxOf(r,g,b); val mn=minOf(r,g,b)
        val sat=if(mx>0)(mx-mn).toFloat()/mx else 0f
        return r>160&&g>160&&b>160 || sat>0.30f&&mx>100
    }

    private fun findLines(
        pixels: List<Triple<Int,Int,Int>>,
        inv: Float,
        pockets: List<Pocket>
    ): List<DetectedLine> {
        val n = pixels.size.coerceAtMost(300)
        val sample = if (pixels.size > n) pixels.shuffled().take(n) else pixels

        var bestP1: Triple<Int,Int,Int>? = null
        var bestP2: Triple<Int,Int,Int>? = null
        var bestInliers = 0

        repeat(50) {
            if (sample.size < 2) return@repeat
            val p1 = sample.random(); val p2 = sample.random()
            if (p1 == p2) return@repeat
            val dx=(p2.first-p1.first).toFloat(); val dy=(p2.second-p1.second).toFloat()
            if (hypot(dx,dy) < 3f) return@repeat
            var inliers = 0
            for (p in sample) {
                if (ptLineDist(p.first.toFloat(),p.second.toFloat(),
                        p1.first.toFloat(),p1.second.toFloat(),
                        p2.first.toFloat(),p2.second.toFloat()) < 2.5f) inliers++
            }
            if (inliers > bestInliers) { bestInliers=inliers; bestP1=p1; bestP2=p2 }
        }

        val p1 = bestP1 ?: return emptyList()
        val p2 = bestP2 ?: return emptyList()
        if (bestInliers < 4) return emptyList()

        val inliers = pixels.filter {
            ptLineDist(it.first.toFloat(),it.second.toFloat(),
                p1.first.toFloat(),p1.second.toFloat(),
                p2.first.toFloat(),p2.second.toFloat()) < 2.5f
        }
        if (inliers.isEmpty()) return emptyList()

        val dx=(p2.first-p1.first).toFloat(); val dy=(p2.second-p1.second).toFloat()
        val len=hypot(dx,dy); val nx=dx/len; val ny=dy/len

        var minT=Float.MAX_VALUE; var maxT=-Float.MAX_VALUE
        var minPx=0f; var minPy=0f; var maxPx=0f; var maxPy=0f
        for (p in inliers) {
            val t=(p.first-p1.first)*nx+(p.second-p1.second)*ny
            if(t<minT){minT=t;minPx=p.first.toFloat();minPy=p.second.toFloat()}
            if(t>maxT){maxT=t;maxPx=p.first.toFloat();maxPy=p.second.toFloat()}
        }

        val startSc=PointF2(minPx*inv,minPy*inv)
        val endSc=PointF2(maxPx*inv,maxPy*inv)
        val ldx=endSc.x-startSc.x; val ldy=endSc.y-startSc.y
        val llen=hypot(ldx,ldy); if(llen<10f) return emptyList()
        val lnx=ldx/llen; val lny=ldy/llen

        val extDist=2000f
        val extEnd=PointF2(endSc.x+lnx*extDist,endSc.y+lny*extDist)

        val nearest=pockets.minByOrNull{p->
            ptLineDist(p.x,p.y,startSc.x,startSc.y,extEnd.x,extEnd.y)
        } ?: return emptyList()

        val distToPocket=ptLineDist(nearest.x,nearest.y,startSc.x,startSc.y,extEnd.x,extEnd.y)
        val willEnter=distToPocket<65f

        val avgR=inliers.map{Color.red(it.third)}.average().toInt()
        val avgG=inliers.map{Color.green(it.third)}.average().toInt()
        val avgB=inliers.map{Color.blue(it.third)}.average().toInt()

        val finalEnd=if(willEnter) PointF2(nearest.x,nearest.y) else extEnd

        return listOf(DetectedLine(startSc,endSc,finalEnd,nearest,willEnter,Color.rgb(avgR,avgG,avgB)))
    }

    private fun ptLineDist(px:Float,py:Float,x1:Float,y1:Float,x2:Float,y2:Float):Float {
        val dx=x2-x1;val dy=y2-y1;val l2=dx*dx+dy*dy
        if(l2<0.001f) return hypot(px-x1,py-y1)
        val t=((px-x1)*dx+(py-y1)*dy)/l2
        val ct=t.coerceIn(0f,1f)
        return hypot(px-x1-ct*dx,py-y1-ct*dy)
    }
}

package com.tac.extend

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat

class ExtendService : Service() {

    companion object { const val CH="tac_ext"; const val NID=88 }

    private var projection: MediaProjection?=null
    private var reader: ImageReader?=null
    private var vDisplay: VirtualDisplay?=null
    private lateinit var wm: WindowManager
    private var overlayView: OverlayView?=null
    private var panelView: View?=null
    private var sw=0; private var sh=0; private var dpi=0
    private var autoMode=false
    private val main=Handler(Looper.getMainLooper())
    private lateinit var bgThread: HandlerThread
    private lateinit var bg: Handler

    private val projCb=object:MediaProjection.Callback(){
        override fun onStop(){cleanup();stopSelf()}
    }

    override fun onCreate() {
        super.onCreate()
        wm=getSystemService(WINDOW_SERVICE) as WindowManager
        bgThread=HandlerThread("ExtBg");bgThread.start();bg=Handler(bgThread.looper)
        refreshMetrics();createChannel()
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        val code=intent?.getIntExtra("code",Activity.RESULT_CANCELED)?:return START_NOT_STICKY
        val data: Intent=if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU)
            intent.getParcelableExtra("data",Intent::class.java)?:return START_NOT_STICKY
        else intent.getParcelableExtra("data")?:return START_NOT_STICKY

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q)
            startForeground(NID,buildNotif(),ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NID,buildNotif())

        cleanup();refreshMetrics()
        val pm=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection=pm.getMediaProjection(code,data)
        projection?.registerCallback(projCb,main)
        reader=ImageReader.newInstance(sw,sh,PixelFormat.RGBA_8888,3)
        vDisplay=projection?.createVirtualDisplay("TacExt",sw,sh,dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,main)

        addOverlay();addPanel()
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun refreshMetrics(){
        val m=DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        sw=m.widthPixels;sh=m.heightPixels;dpi=m.densityDpi
    }

    private fun addOverlay(){
        val v=OverlayView(this)
        val lp=WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try{wm.addView(v,lp);overlayView=v}catch(e:Exception){e.printStackTrace()}
    }

    private fun addPanel(){
        val root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(215,8,8,8));setPadding(12,12,12,12)
        }
        val title=TextView(this).apply{
            text="TacExtend";setTextColor(Color.argb(255,0,255,120))
            textSize=12f;gravity=Gravity.CENTER
        }
        val btnScan=Button(this).apply{
            text="ESCANEAR";setTextColor(Color.BLACK);textSize=12f
            backgroundTintList=android.content.res.ColorStateList.valueOf(Color.argb(255,0,210,80))
        }
        val btnClear=Button(this).apply{
            text="LIMPAR";setTextColor(Color.WHITE);textSize=11f
            backgroundTintList=android.content.res.ColorStateList.valueOf(Color.argb(200,60,60,60))
        }
        val tbAuto=ToggleButton(this).apply{
            textOff="AUTO OFF";textOn="AUTO ON"
            isChecked=false;setTextColor(Color.WHITE);textSize=10f
            backgroundTintList=android.content.res.ColorStateList.valueOf(Color.argb(200,40,40,40))
        }
        val tvStatus=TextView(this).apply{
            text="Mire e toque Escanear"
            setTextColor(Color.GRAY);textSize=10f;gravity=Gravity.CENTER
        }
        root.addView(title,lp2(mb=8))
        root.addView(btnScan,lp2(mb=6))
        root.addView(btnClear,lp2(mb=6))
        root.addView(tbAuto,lp2(mb=4))
        root.addView(tvStatus,lp2())

        val wlp=WindowManager.LayoutParams(
            230,WindowManager.LayoutParams.WRAP_CONTENT,
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT
        ).apply{gravity=Gravity.TOP or Gravity.END;x=8;y=160}

        var ox=0f;var oy=0f;var sx=0f;var sy=0f
        root.setOnTouchListener{v,e->
            when(e.action){
                MotionEvent.ACTION_DOWN->{sx=e.rawX;sy=e.rawY;ox=wlp.x.toFloat();oy=wlp.y.toFloat();true}
                MotionEvent.ACTION_MOVE->{
                    wlp.x=(ox-(e.rawX-sx)).toInt();wlp.y=(oy+(e.rawY-sy)).toInt()
                    try{wm.updateViewLayout(v,wlp)}catch(_:Exception){};true
                }
                else->false
            }
        }

        fun status(msg:String,color:Int)=main.post{tvStatus.text=msg;tvStatus.setTextColor(color)}

        btnScan.setOnClickListener{
            status("Escaneando...",Color.YELLOW)
            doScan{msg,ok->status(msg,if(ok)Color.GREEN else Color.RED)}
        }
        btnClear.setOnClickListener{overlayView?.clear();status("Limpo",Color.GRAY)}
        tbAuto.setOnCheckedChangeListener{_,on->
            autoMode=on
            if(on){status("AUTO ligado",Color.GREEN);scheduleAuto(tvStatus)}
            else status("AUTO desligado",Color.GRAY)
        }

        try{wm.addView(root,wlp);panelView=root}catch(e:Exception){e.printStackTrace()}
    }

    private fun scheduleAuto(tv:TextView){
        if(!autoMode) return
        main.postDelayed({
            doScan{msg,ok->main.post{tv.text=msg;tv.setTextColor(if(ok)Color.GREEN else Color.RED)}}
            scheduleAuto(tv)
        },800L)
    }

    private fun doScan(cb:(String,Boolean)->Unit){
        val img=reader?.acquireLatestImage()?:run{cb("Sem imagem",false);return}
        try{
            val plane=img.planes[0];val bw=plane.rowStride/plane.pixelStride
            val bmp=Bitmap.createBitmap(bw,img.height,Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(plane.buffer)
            val final=if(bw!=img.width)
                Bitmap.createBitmap(bmp,0,0,img.width,img.height).also{bmp.recycle()} else bmp
            bg.post{
                try{
                    val(lines,pockets)=LineDetector.detect(final)
                    if(!final.isRecycled) final.recycle()
                    main.post{overlayView?.update(lines,pockets)}
                    if(lines.isEmpty()) cb("Nenhuma linha detectada",false)
                    else{
                        val n=lines.count{it.willScore}
                        cb("${n} linha(s) entrando!",n>0)
                    }
                }catch(e:Exception){
                    e.printStackTrace()
                    try{if(!final.isRecycled)final.recycle()}catch(_:Exception){}
                    cb("Erro",false)
                }
            }
        }finally{try{img.close()}catch(_:Exception){}}
    }

    private fun cleanup(){
        try{vDisplay?.release()}catch(_:Exception){};vDisplay=null
        try{reader?.close()}catch(_:Exception){};reader=null
    }

    override fun onDestroy(){
        autoMode=false;main.removeCallbacksAndMessages(null)
        try{overlayView?.let{wm.removeView(it)}}catch(_:Exception){};overlayView=null
        try{panelView?.let{wm.removeView(it)}}catch(_:Exception){};panelView=null
        cleanup()
        try{projection?.unregisterCallback(projCb)}catch(_:Exception){}
        try{projection?.stop()}catch(_:Exception){};projection=null
        try{bgThread.quitSafely()}catch(_:Exception){}
        super.onDestroy()
    }

    override fun onBind(i:Intent?)=null
    private fun lp2(mb:Int=0)=LinearLayout.LayoutParams(-1,-2).also{it.bottomMargin=mb}

    private fun createChannel(){
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(CH,"TacExtend",NotificationManager.IMPORTANCE_LOW))
    }
    private fun buildNotif()=NotificationCompat.Builder(this,CH)
        .setContentTitle("TacExtend ativo").setContentText("Mire e toque Escanear")
        .setSmallIcon(android.R.drawable.ic_menu_compass)
        .setPriority(NotificationCompat.PRIORITY_LOW).build()
}

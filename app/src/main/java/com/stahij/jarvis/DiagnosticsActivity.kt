package com.stahij.jarvis

import android.app.*
import android.os.*
import android.content.*
import android.graphics.*
import android.view.*
import android.widget.*
import org.json.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

class DiagnosticsActivity : Activity() {
 private val h=Handler(Looper.getMainLooper()); private val samples=JSONArray()
 private lateinit var labels:TextView; private var lastCpu=cpu(); private var lastProc=proc()
 private val tick=object:Runnable { override fun run(){ refresh(); h.postDelayed(this,1500) } }
 override fun onCreate(b:Bundle?){super.onCreate(b); window.statusBarColor=Color.rgb(5,10,22); window.navigationBarColor=Color.rgb(5,10,22)
  val root=LinearLayout(this).apply{orientation=1;setPadding(18,18,18,18);setBackgroundColor(Color.rgb(5,10,22))}
  root.addView(TextView(this).apply{text="JARVIS / SYSTEM MONITOR  •  LIVE";textSize=18f;setTextColor(Color.CYAN)})
  root.addView(TextView(this).apply{text="Автообновление каждые 1,5 секунды";textSize=11f;setTextColor(Color.LTGRAY)})
  labels=TextView(this).apply{textSize=17f;setTextColor(Color.WHITE);setPadding(0,24,0,24)};root.addView(labels,LinearLayout.LayoutParams(-1,0,1f))
  root.addView(Button(this).apply{text="Сохранить отчёт TXT";setOnClickListener{export(false)}})
  root.addView(Button(this).apply{text="Сохранить отчёт JSON";setOnClickListener{export(true)}})
  root.addView(Button(this).apply{text="Закрыть";setOnClickListener{finish()}});setContentView(root);refresh()
 }
 private fun cpu():Long?=try{File("/proc/stat").readLines()[0].trim().split(Regex("\\s+")).drop(1).sumOf{it.toLong()}}catch(e:Exception){null}
 private fun proc():Long?=try{val s=File("/proc/self/stat").readText();val a=s.substring(s.lastIndexOf(')')+2).split(Regex("\\s+"));a[11].toLong()+a[12].toLong()}catch(e:Exception){null}
 private fun refresh(){val am=getSystemService(ACTIVITY_SERVICE) as ActivityManager;val mi=ActivityManager.MemoryInfo();am.getMemoryInfo(mi);val c=cpu();val p=proc();var pc=0.0
  if(c!=null&&p!=null&&lastCpu!=null&&lastProc!=null){val dt=(c-lastCpu!!).coerceAtLeast(1);pc=((p-lastProc!!).coerceAtLeast(0).toDouble()/dt*100*Runtime.getRuntime().availableProcessors()).coerceIn(0.0,100.0)};lastCpu=c;lastProc=p
  val bat=registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED));val temp=(bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,-1)?:-1)/10.0
  val dm=Debug.MemoryInfo();Debug.getMemoryInfo(dm);val j=(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576
  val th=if(Build.VERSION.SDK_INT>=29){val pm=getSystemService(POWER_SERVICE) as PowerManager;pm.currentThermalStatus.toString()}else "Недоступно"
  val total=mi.totalMem/1048576;val used=(mi.totalMem-mi.availMem)/1048576
  labels.text="CPU процесса: "+String.format(Locale.US,"%.1f%%",pc)+"\n\nRAM: "+used+" / "+total+" МБ\nСвободно: "+mi.availMem/1048576+" МБ\n\nБатарея: "+(if(temp>=0)temp.toString()+" °C" else "нет данных")+"\n\nПамять Jarvis PSS: "+dm.totalPss/1024+" МБ\nJava heap: "+j+" МБ\nNative heap: "+Debug.getNativeHeapAllocatedSize()/1048576+" МБ\n\nThermal status Android: "+th+"\n\nТочек собрано: "+(samples.length()+1)+"\n\nCPU — оценка процесса, температура — батареи, не SoC."
  samples.put(JSONObject().put("time",System.currentTimeMillis()).put("cpu_process_percent",pc).put("ram_total_mb",total).put("ram_used_mb",used).put("ram_free_mb",mi.availMem/1048576).put("battery_temp_c",temp).put("pss_mb",dm.totalPss/1024).put("java_heap_mb",j).put("native_heap_mb",Debug.getNativeHeapAllocatedSize()/1048576).put("thermal_status",th));while(samples.length()>240)samples.remove(0)
 }
 private fun export(json:Boolean){val stamp=SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(Date());val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=if(json)"application/json" else "text/plain";putExtra(Intent.EXTRA_TITLE,"jarvis_diagnostics_"+stamp+(if(json)".json" else ".txt"))};startActivityForResult(i,if(json)802 else 801)}
 @Deprecated("Document picker callback") override fun onActivityResult(r:Int,s:Int,d:Intent?){super.onActivityResult(r,s,d);if(s!=RESULT_OK||d?.data==null||r !in 801..802)return;try{val events=JSONArray(getSharedPreferences("jarvis_diagnostics",MODE_PRIVATE).getString("events","[]"));val out=if(r==802)JSONObject().put("app","Jarvis").put("version","1.3.0").put("device",Build.MODEL).put("android",Build.VERSION.RELEASE).put("samples",samples).put("events",events).put("privacy","No chat content or credentials").toString(2) else buildString{append("JARVIS DIAGNOSTICS 1.3.0\nDevice: "+Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n");for(x in 0 until samples.length())append(samples.optJSONObject(x).toString()+"\n");append("\nEVENTS\n");for(x in 0 until events.length())append(events.optJSONObject(x).toString()+"\n");append("\nNo chat text or credentials included.\n")};contentResolver.openOutputStream(d.data!!)?.use{it.write(out.toByteArray(Charsets.UTF_8))};Toast.makeText(this,"Отчёт сохранён",Toast.LENGTH_LONG).show()}catch(e:Exception){Toast.makeText(this,"Ошибка: "+e.message,Toast.LENGTH_LONG).show()}}
 override fun onResume(){super.onResume();h.post(tick)};override fun onPause(){h.removeCallbacks(tick);super.onPause()}
}

package ir.hamed.klox;
import android.app.*;import android.content.*;import android.graphics.Typeface;import android.net.Uri;import android.os.*;import android.provider.Settings;import android.view.*;import android.widget.*;import java.util.*;
public class SettingsActivity extends Activity{private SharedPreferences prefs;private LinearLayout root,slotContainer;private SeekBar fontSize;private TextView preview;private static final int SLOTS=7; private static final int PICK_REMINDER_AUDIO=7001;
@Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_settings);prefs=getSharedPreferences("settings",MODE_PRIVATE);root=findViewById(R.id.root);slotContainer=findViewById(R.id.slotContainer);Switch master=findViewById(R.id.master);master.setChecked(prefs.getBoolean("enabled",true));master.setOnCheckedChangeListener((v,on)->{prefs.edit().putBoolean("enabled",on).apply();if(on)ScheduleManager.scheduleNext(this);else ScheduleManager.cancel(this);});buildSlots();buildDingSelection();buildVolume();buildSpeed();buildDateMode();buildPersistentNotification();buildReminder();buildAppearance();findViewById(R.id.back).setOnClickListener(v->finish());if(Build.VERSION.SDK_INT>=31){try{AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);if(am!=null&&!am.canScheduleExactAlarms())startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));}catch(Exception ignored){}}}
private void buildSlots(){LayoutInflater inf=LayoutInflater.from(this);for(int s=0;s<SLOTS;s++){View v=inf.inflate(R.layout.slot_item,slotContainer,false);slotContainer.addView(v);final int idx=s;TextView title=v.findViewById(R.id.slotTitle);Switch en=v.findViewById(R.id.enabled);Button start=v.findViewById(R.id.start),end=v.findViewById(R.id.end);SeekBar bar=v.findViewById(R.id.interval);TextView label=v.findViewById(R.id.intervalLabel);title.setText("بازه "+(s+1));boolean def=s==0;en.setChecked(prefs.getBoolean("slot"+s+"Enabled",def));int defStart=s==0?420:s==1?840:s==2?1200:0;int defEnd=s==0?600:s==1?1200:s==2?1440:s==3?360:60;int st=prefs.getInt("slot"+s+"Start",defStart),ed=prefs.getInt("slot"+s+"End",defEnd),inter=prefs.getInt("slot"+s+"Interval",30);start.setText("شروع: "+fmt(st));end.setText("پایان: "+fmt(ed));bar.setProgress(Math.max(0,Math.min(119,inter-1)));label.setText("فاصله اعلام: "+inter+" دقیقه");en.setOnCheckedChangeListener((x,on)->{prefs.edit().putBoolean("slot"+idx+"Enabled",on).apply();ScheduleManager.scheduleNext(this);});start.setOnClickListener(x->pickTime(idx,true,start));end.setOnClickListener(x->pickTime(idx,false,end));bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean f){int m=p+1;label.setText("فاصله اعلام: "+m+" دقیقه");if(f)prefs.edit().putInt("slot"+idx+"Interval",m).apply();}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){prefs.edit().putInt("slot"+idx+"Interval",b.getProgress()+1).apply();ScheduleManager.scheduleNext(SettingsActivity.this);}});}}
private void pickTime(int idx,boolean startFlag,Button button){int old=prefs.getInt("slot"+idx+(startFlag?"Start":"End"),startFlag?420:600);int h=(old/60)%24,m=old%60;new TimePickerDialog(this,(view,hh,mm)->{int val=hh*60+mm;prefs.edit().putInt("slot"+idx+(startFlag?"Start":"End"),val).apply();button.setText((startFlag?"شروع: ":"پایان: ")+fmt(val));ScheduleManager.scheduleNext(this);},h,m,true).show();}
private String fmt(int m){m=((m%1440)+1440)%1440;return String.format(Locale.US,"%02d:%02d",m/60,m%60).replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳').replace('4','۴').replace('5','۵').replace('6','۶').replace('7','۷').replace('8','۸').replace('9','۹');}
private void buildDingSelection(){
RadioGroup group=findViewById(R.id.dingChoices);
int saved=prefs.getInt("dingNumber",1);
int[] ids={R.id.ding1,R.id.ding2,R.id.ding3,R.id.ding4,R.id.ding5};
if(saved<1||saved>5)saved=1;
group.check(ids[saved-1]);
group.setOnCheckedChangeListener((g,id)->{
for(int i=0;i<ids.length;i++){if(id==ids[i]){prefs.edit().putInt("dingNumber",i+1).apply();break;}}
});
findViewById(R.id.testDing).setOnClickListener(v->{ if(speakerForTest==null) speakerForTest=new AudioTimeSpeaker(this); speakerForTest.testSelectedDing(); });
}
private AudioTimeSpeaker speakerForTest;

private void buildDateMode(){
 RadioGroup g=findViewById(R.id.dateMode);
 String saved=prefs.getString("dateMode","jalali");
 g.check("gregorian".equals(saved)?R.id.dateGregorian:R.id.dateJalali);
 g.setOnCheckedChangeListener((x,id)->{
   prefs.edit().putString("dateMode",id==R.id.dateGregorian?"gregorian":"jalali").apply();
 });
}
private void buildPersistentNotification(){
 Switch sw=findViewById(R.id.persistentNotification);
 sw.setChecked(prefs.getBoolean("persistentNotification",false));
 sw.setOnCheckedChangeListener((b,on)->{
   prefs.edit().putBoolean("persistentNotification",on).apply();
   MainActivity.updatePersistentNotification(this);
 });
}
private void buildReminder(){
 Switch sw=findViewById(R.id.reminderEnabled);
 sw.setChecked(prefs.getBoolean("reminderEnabled",false));
 TextView text=findViewById(R.id.reminderText);
 text.setText(prefs.getString("reminderText",""));
 Button time=findViewById(R.id.reminderTime);
 int old=prefs.getInt("reminderMinute",9*60);
 time.setText("زمان: "+fmt(old));
 time.setOnClickListener(v->{
   int h=old/60,m=old%60;
   new TimePickerDialog(this,(view,hh,mm)->{
     int val=hh*60+mm;
     prefs.edit().putInt("reminderMinute",val).apply();
     time.setText("زمان: "+fmt(val));
     ReminderManager.schedule(this);
   },h,m,true).show();
 });
 findViewById(R.id.saveReminder).setOnClickListener(v->{
   prefs.edit().putString("reminderText",text.getText().toString().trim()).apply();
   ReminderManager.schedule(this);
   Toast.makeText(this,"ياديده شد",Toast.LENGTH_SHORT).show();
 });
 findViewById(R.id.pickReminderAudio).setOnClickListener(v->{
   Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
   i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("audio/*");
   startActivityForResult(i,PICK_REMINDER_AUDIO);
 });
 findViewById(R.id.testReminder).setOnClickListener(v->ReminderManager.fireNow(this));
}
@Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
 super.onActivityResult(requestCode,resultCode,data);
 if(requestCode==PICK_REMINDER_AUDIO && resultCode==RESULT_OK && data!=null && data.getData()!=null){
   Uri u=data.getData();
   try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
   prefs.edit().putString("reminderAudioUri",u.toString()).apply();
   Toast.makeText(this,"صدای یادآوری انتخاب شد",Toast.LENGTH_SHORT).show();
 }
}

private void buildVolume(){SeekBar v=findViewById(R.id.volume);TextView l=findViewById(R.id.volumeLabel);int saved=prefs.getInt("announcementVolume",80);v.setProgress(Math.max(0,Math.min(80,saved-20)));l.setText(toFa(saved)+"٪");v.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){int val=p+20;l.setText(toFa(val)+"٪");if(f)prefs.edit().putInt("announcementVolume",val).apply();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){prefs.edit().putInt("announcementVolume",s.getProgress()+20).apply();}});}
private void buildSpeed(){
SeekBar v=findViewById(R.id.speed); TextView l=findViewById(R.id.speedLabel);
int saved=prefs.getInt("announcementSpeed",100); saved=Math.max(70,Math.min(130,saved));
v.setProgress(saved-70); l.setText(toFa(saved)+"٪");
v.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
public void onProgressChanged(SeekBar s,int p,boolean f){int val=p+70;l.setText(toFa(val)+"٪");if(f)prefs.edit().putInt("announcementSpeed",val).apply();}
public void onStartTrackingTouch(SeekBar s){}
public void onStopTrackingTouch(SeekBar s){prefs.edit().putInt("announcementSpeed",s.getProgress()+70).apply();}
});
}
@Override protected void onDestroy(){if(speakerForTest!=null)speakerForTest.stop();super.onDestroy();}
private void open(String u){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u)));}catch(Exception ignored){}}
private void buildAppearance(){fontSize=findViewById(R.id.fontSize);preview=findViewById(R.id.fontPreview);float saved=prefs.getFloat("fontSize",56);fontSize.setProgress(Math.max(0,Math.min(36,Math.round(saved-32))));fontSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){float z=32+p;prefs.edit().putFloat("fontSize",z).apply();preview.setTextSize(z/2);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});RadioGroup colors=findViewById(R.id.colors);int c=prefs.getInt("color",0xffd89b2b);if(c==0xff66bb6a)colors.check(R.id.green);else if(c==0xffce93d8)colors.check(R.id.purple);else if(c==0xff8ab4f8)colors.check(R.id.blue);else colors.check(R.id.orange);colors.setOnCheckedChangeListener((g,id)->{int col=id==R.id.green?0xff66bb6a:id==R.id.purple?0xffce93d8:id==R.id.blue?0xff8ab4f8:0xffd89b2b;prefs.edit().putInt("color",col).apply();preview.setTextColor(col);});try{Typeface tf=Typeface.createFromAsset(getAssets(),"fonts/YEKAN.TTF");applyTypeface(root,tf);}catch(Exception ignored){}}
private void applyTypeface(View v,Typeface tf){if(v instanceof TextView)((TextView)v).setTypeface(tf);if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)applyTypeface(((ViewGroup)v).getChildAt(i),tf);}
private String toFa(int n){return String.valueOf(n).replace('0','۰').replace('1','۱').replace('2','۲').replace('3','۳').replace('4','۴').replace('5','۵').replace('6','۶').replace('7','۷').replace('8','۸').replace('9','۹');}
}

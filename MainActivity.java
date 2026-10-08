package com.maya.ai;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.speech.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private SpeechRecognizer recognizer; private TextToSpeech tts; private TextView status;
    private boolean listening=false; private CameraManager camera; private String cameraId;
    private DevicePolicyManager dpm; private ComponentName admin;
    private final ArrayList<AppInfo> apps=new ArrayList<>();

    static class AppInfo { String label,pkg; AppInfo(String l,String p){label=l;pkg=p;} }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(32,48,32,32);
        TextView title=new TextView(this); title.setText("Maya AI 2.0"); title.setTextSize(32); box.addView(title);
        status=new TextView(this); status.setText("বলুন: “Maya, YouTube খোলো”"); status.setTextSize(18); status.setPadding(0,20,0,20); box.addView(status);
        Button speak=new Button(this); speak.setText("🎙️ কথা বলুন"); speak.setOnClickListener(v->listen()); box.addView(speak);
        Button adminBtn=new Button(this); adminBtn.setText("🔒 Phone Lock permission"); adminBtn.setOnClickListener(v->requestAdmin()); box.addView(adminBtn);
        Button refresh=new Button(this); refresh.setText("🔄 App list refresh"); refresh.setOnClickListener(v->{loadApps(); speak("অ্যাপ লিস্ট আপডেট করেছি");}); box.addView(refresh);
        TextView help=new TextView(this); help.setPadding(0,20,0,0);
        help.setText("উদাহরণ:\\n• “YouTube খোলো”\\n• “TikTok খোলো”\\n• “Free Fire খোলো”\\n• “Copilot খোলো”\\n• “Computer Plus খোলো”\\n• “Flashlight on করো”\\n• “Volume বাড়াও”\\n• “Phone lock করো”\\n• “Call 01XXXXXXXXX”");
        box.addView(help); setContentView(box);

        dpm=(DevicePolicyManager)getSystemService(DEVICE_POLICY_SERVICE); admin=new ComponentName(this,MayaDeviceAdminReceiver.class);
        camera=(CameraManager)getSystemService(CAMERA_SERVICE);
        try{String[] ids=camera.getCameraIdList(); if(ids.length>0) cameraId=ids[0];}catch(Exception ignored){}
        tts=new TextToSpeech(this,s->{});
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);
        recognizer=SpeechRecognizer.createSpeechRecognizer(this);
        recognizer.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle p){setStatus("শুনছি…");} public void onBeginningOfSpeech(){} public void onRmsChanged(float r){}
            public void onBufferReceived(byte[] b){} public void onEndOfSpeech(){listening=false;}
            public void onError(int e){listening=false;setStatus("আবার চেষ্টা করুন।");}
            public void onResults(Bundle r){listening=false; ArrayList<String>x=r.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION); if(x!=null&&!x.isEmpty())handle(x.get(0));}
            public void onPartialResults(Bundle b){} public void onEvent(int a,Bundle b){}
        });
        loadApps();
    }

    private void loadApps(){
        apps.clear();
        PackageManager pm=getPackageManager();
        Intent main=new Intent(Intent.ACTION_MAIN); main.addCategory(Intent.CATEGORY_LAUNCHER);
        for(ResolveInfo ri:pm.queryIntentActivities(main,PackageManager.MATCH_ALL)){
            String pkg=ri.activityInfo.packageName, label=ri.loadLabel(pm).toString();
            if(!pkg.equals(getPackageName())) apps.add(new AppInfo(label,pkg));
        }
        Collections.sort(apps,(a,b)->a.label.compareToIgnoreCase(b.label));
    }

    private void listen(){
        if(listening)return; listening=true;
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"bn-BD"); i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Maya-কে বলুন…");
        recognizer.startListening(i);
    }

    private void handle(String raw){
        String s=norm(raw); setStatus("আপনি বলেছেন: "+raw);
        try{
            if((s.contains("flash")||s.contains("ফ্ল্যাশ"))&&(s.contains("on")||s.contains("চালু"))){flash(true);speak("ফ্ল্যাশ চালু করেছি");return;}
            if((s.contains("flash")||s.contains("ফ্ল্যাশ"))&&(s.contains("off")||s.contains("বন্ধ"))){flash(false);speak("ফ্ল্যাশ বন্ধ করেছি");return;}
            if((s.contains("volume")||s.contains("ভলিউম"))&&(s.contains("up")||s.contains("বাড়"))){volume(1);return;}
            if((s.contains("volume")||s.contains("ভলিউম"))&&(s.contains("down")||s.contains("কম"))){volume(-1);return;}
            if(s.contains("lock")||s.contains("লক")){lockPhone();return;}
            if(s.startsWith("call ")||s.startsWith("কল ")||s.contains("call ")){String num=s.replaceAll("[^0-9+]",""); if(num.length()>=6)call(num);else speak("নম্বরটি পরিষ্কারভাবে বলুন");return;}
            if(s.contains("গান ")||s.contains("song ")){String q=s.replaceFirst("(?i)^(গান|song)\\s*","");searchYouTube(q);return;}

            String target=s.replaceAll("(?i)^(maya\\s*)?","")
                .replaceAll("(?i)(খোলো|খুলে দাও|open|চালু করো|চালু কর|open করো|launch|ওপেন)","")
                .trim();
            AppInfo match=findApp(target);
            if(match!=null){Intent launch=getPackageManager().getLaunchIntentForPackage(match.pkg); if(launch!=null){startActivity(launch);speak(match.label+" খুলছি");return;}}
            Intent web=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(raw)));
            startActivity(web); speak("অ্যাপটি খুঁজে পাইনি, ওয়েবে খুঁজছি");
        }catch(Exception e){speak("এই কাজটি করা গেল না");}
    }

    private String norm(String x){return x.toLowerCase(Locale.ROOT).replace("।","").trim();}
    private AppInfo findApp(String target){
        if(target.length()==0)return null;
        String t=target.toLowerCase(Locale.ROOT);
        for(AppInfo a:apps) if(a.label.toLowerCase(Locale.ROOT).equals(t)) return a;
        for(AppInfo a:apps) if(a.label.toLowerCase(Locale.ROOT).contains(t)||t.contains(a.label.toLowerCase(Locale.ROOT))) return a;
        // common English/Bangla aliases
        String[][] aliases={{"ইউটিউব","youtube"},{"ইউটিউব","youtube"},{"টিকটক","tiktok"},{"ইনস্টাগ্রাম","instagram"},{"হোয়াটসঅ্যাপ","whatsapp"},{"ফ্রি ফায়ার","free fire"},{"ফ্রিফায়ার","free fire"},{"কোপাইলট","copilot"},{"কম্পিউটার প্লাস","computer plus"}};
        for(String[] al:aliases) if(t.contains(al[0])) for(AppInfo a:apps) if(a.label.toLowerCase(Locale.ROOT).contains(al[1])) return a;
        return null;
    }
    private void searchYouTube(String q){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.youtube.com/results?search_query="+Uri.encode(q))));}
    private void call(String n){if(checkSelfPermission(Manifest.permission.CALL_PHONE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CALL_PHONE},11);return;}startActivity(new Intent(Intent.ACTION_CALL,Uri.parse("tel:"+n)));}
    private void volume(int d){android.media.AudioManager am=(android.media.AudioManager)getSystemService(AUDIO_SERVICE);am.adjustVolume(d>0?1:-1,0);speak(d>0?"ভলিউম বাড়িয়েছি":"ভলিউম কমিয়েছি");}
    private void flash(boolean on)throws Exception{if(cameraId!=null)camera.setTorchMode(cameraId,on);}
    private void requestAdmin(){Intent i=new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);i.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,admin);i.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,"Maya-কে ফোন lock করার অনুমতি দিন।");startActivityForResult(i,12);}
    private void lockPhone(){if(dpm.isAdminActive(admin))dpm.lockNow();else{ speak("আগে Lock permission চালু করুন");requestAdmin();}}
    private void speak(String x){if(tts!=null)tts.speak(x,TextToSpeech.QUEUE_FLUSH,null,"maya");}
    private void setStatus(String x){if(status!=null)status.setText(x);}
    @Override protected void onDestroy(){if(recognizer!=null)recognizer.destroy();if(tts!=null)tts.shutdown();super.onDestroy();}
}

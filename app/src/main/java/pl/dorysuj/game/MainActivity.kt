package pl.dorysuj.game

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.*
import kotlin.random.Random

class MainActivity : Activity() {
    private lateinit var game: DrawGameView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        game = DrawGameView(this)
        setContentView(game)
    }
    override fun onPause() { super.onPause(); game.persist() }
    override fun onBackPressed() { if (game.goHome()) return; super.onBackPressed() }
}

private data class Puzzle(val title: String, val hint: String, val kind: Int, val missing: String)

private class DrawGameView(context: Context) : View(context) {
    private val prefs = context.getSharedPreferences("dorysuj_save", Context.MODE_PRIVATE)
    private val bg = Color.rgb(16, 24, 39)
    private val panel = Color.rgb(29, 41, 61)
    private val mint = Color.rgb(55, 220, 177)
    private val yellow = Color.rgb(255, 207, 92)
    private val white = Color.WHITE
    private val muted = Color.rgb(180, 194, 212)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths = mutableListOf<Path>()
    private var currentPath: Path? = null
    private var screen = 0 // 0 home, 1 play, 2 levels, 3 settings
    private var level = prefs.getInt("level", 1).coerceIn(1, 130)
    private var unlocked = prefs.getInt("unlocked", 1).coerceIn(1, 130)
    private var coins = prefs.getInt("coins", 0).coerceAtLeast(0)
    private var solved = false
    private var hintShown = false
    private var soundOn = prefs.getBoolean("sound", true)
    private var name = prefs.getString("player_name", "") ?: ""
    private var avatar = prefs.getInt("avatar", 0)
    private var strokes = 0
    private var showMessage = ""
    private var messageUntil = 0L
    private val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
    private val puzzles = makePuzzles()

    private fun makePuzzles(): List<Puzzle> {
        val base = listOf(
            Puzzle("Dorysuj promień słońca", "Słońce potrzebuje jeszcze jednego promienia.", 0, "promień"),
            Puzzle("Dorysuj koło", "Piłka powinna być okrągła.", 1, "okrąg"),
            Puzzle("Dorysuj dach", "Dom potrzebuje dachu.", 2, "dach"),
            Puzzle("Dorysuj łodygę", "Kwiatowi brakuje łodygi.", 3, "łodyga"),
            Puzzle("Dorysuj uśmiech", "Ta buźka potrzebuje uśmiechu.", 4, "uśmiech"),
            Puzzle("Dorysuj uchwyt", "Kubek powinien mieć uchwyt.", 5, "uchwyt"),
            Puzzle("Dorysuj ogon", "Zwierzakowi brakuje ogona.", 6, "ogon"),
            Puzzle("Dorysuj płotek", "Dokończ brakujący fragment płotka.", 7, "płotek"),
            Puzzle("Dorysuj płatek", "Kwiat ma jeden płatek mniej.", 8, "płatek"),
            Puzzle("Dorysuj gwiazdę", "Dokończ brakujący fragment gwiazdy.", 9, "gwiazda"),
            Puzzle("Dorysuj falę", "Na wodzie brakuje fali.", 10, "fala"),
            Puzzle("Dorysuj skrzydło", "Motyl potrzebuje drugiego skrzydła.", 11, "skrzydło"),
            Puzzle("Dorysuj komin", "Na dachu brakuje komina.", 12, "komin"),
            Puzzle("Dorysuj schodek", "Uzupełnij schody.", 13, "schodek"),
            Puzzle("Dorysuj księżyc", "Nocne niebo nie jest jeszcze kompletne.", 14, "księżyc"),
            Puzzle("Dorysuj listek", "Roślinie brakuje liścia.", 15, "listek"),
            Puzzle("Dorysuj koło w rowerze", "Rower potrzebuje drugiego koła.", 16, "koło"),
            Puzzle("Dorysuj drzwi", "Dom nie ma drzwi.", 17, "drzwi"),
            Puzzle("Dorysuj chmurę", "Dokończ obrys chmurki.", 18, "chmura"),
            Puzzle("Dorysuj most", "Połącz dwa brzegi.", 19, "most")
        )
        return List(130) { i ->
            val p = base[i % base.size]
            val tier = i / base.size + 1
            if (i < base.size) p else Puzzle("Poziom ${i + 1}: ${p.title.removePrefix("Dorysuj ")}", "Wskazówka: ${p.hint} Poziom trudności ${tier}.", p.kind, p.missing)
        }
    }

    fun persist() { prefs.edit().putInt("level", level).putInt("unlocked", unlocked).putInt("coins", coins).putBoolean("sound", soundOn).putString("player_name", name).putInt("avatar", avatar).apply() }
    fun goHome(): Boolean { if (screen != 0) { screen = 0; invalidate(); return true }; return false }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bg)
        when (screen) {
            0 -> drawHome(canvas)
            1 -> drawGame(canvas)
            2 -> drawLevels(canvas)
            else -> drawSettings(canvas)
        }
        if (showMessage.isNotEmpty() && System.currentTimeMillis() < messageUntil) drawToast(canvas, showMessage)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = white, bold: Boolean = false, center: Boolean = false) {
        paint.color = color; paint.textSize = size; paint.typeface = if (bold) Typeface.create("sans-serif", Typeface.BOLD) else Typeface.create("sans-serif", Typeface.NORMAL); paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT; paint.style = Paint.Style.FILL
        c.drawText(s, x, y, paint)
    }
    private fun round(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int, radius: Float = 24f) { paint.color = color; paint.style = Paint.Style.FILL; c.drawRoundRect(l,t,r,b,radius,radius,paint) }
    private fun button(c: Canvas, label: String, l: Float, t: Float, r: Float, b: Float, color: Int = mint) { round(c,l,t,r,b,color,22f); text(c,label,(l+r)/2,(t+b)/2+9f,21f,bg,true,true) }
    private fun drawHome(c: Canvas) {
        text(c,"DORYSUJ!",width/2f, height*.17f, 44f, mint,true,true)
        text(c,"Każdy obrazek ma brakujący element.",width/2f,height*.22f,17f,muted,false,true)
        round(c,width*.12f,height*.29f,width*.88f,height*.48f,panel,30f)
        drawMiniSample(c,width/2f,height*.38f)
        text(c,if(name.isBlank()) "Gotowy na rysowanie?" else "Cześć, $name!",width/2f,height*.45f,20f,white,true,true)
        button(c,"GRAJ",width*.15f,height*.55f,width*.85f,height*.63f)
        button(c,"WYBIERZ POZIOM",width*.15f,height*.66f,width*.85f,height*.74f,yellow)
        button(c,"USTAWIENIA",width*.15f,height*.77f,width*.85f,height*.85f,Color.rgb(105,139,255))
        text(c,"⭐  ${coins} monet     Poziom $level / 130",width/2f,height*.92f,17f,muted,true,true)
    }
    private fun drawMiniSample(c: Canvas, cx: Float, cy: Float) {
        paint.style=Paint.Style.STROKE; paint.strokeWidth=6f; paint.strokeCap=Paint.Cap.ROUND; paint.color=yellow
        c.drawCircle(cx,cy,35f,paint)
        for (a in listOf(-140f,-90f,-40f,10f,40f,140f)) { val rad=Math.toRadians(a.toDouble()); c.drawLine(cx+42*cos(rad).toFloat(),cy+42*sin(rad).toFloat(),cx+55*cos(rad).toFloat(),cy+55*sin(rad).toFloat(),paint) }
        paint.style=Paint.Style.FILL
    }
    private fun drawGame(c: Canvas) {
        text(c,"‹ MENU",24f,48f,19f,mint,true)
        text(c,"POZIOM $level / 130",width/2f,48f,20f,white,true,true)
        round(c,width-145f,20f,width-20f,62f,panel,20f); text(c,"🟡 $coins",width-82f,48f,18f,yellow,true,true)
        round(c,18f,78f,width-18f,height*.19f,panel,24f)
        val p = puzzles[level-1]
        text(c,p.title,width/2f,115f,if(width<400)20f else 23f,white,true,true)
        text(c,splitText(p.hint, width*.82f, 16f).firstOrNull() ?: p.hint,width/2f,145f,15f,muted,false,true)
        val cx=width/2f; val cy=height*.43f; val scale=min(width*.33f, height*.18f)
        drawPuzzle(c,p.kind,cx,cy,scale,solved)
        // user drawing surface
        paint.color=Color.argb(40,255,255,255); paint.style=Paint.Style.STROKE; paint.strokeWidth=2f; paint.pathEffect=DashPathEffect(floatArrayOf(8f,9f),0f)
        c.drawRoundRect(width*.10f,height*.29f,width*.90f,height*.57f,24f,24f,paint); paint.pathEffect=null
        paint.color=yellow; paint.style=Paint.Style.STROKE; paint.strokeWidth=7f; paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        paths.forEach { c.drawPath(it,paint) }; currentPath?.let { c.drawPath(it,paint) }; paint.style=Paint.Style.FILL
        if (solved) {
            round(c,width*.12f,height*.59f,width*.88f,height*.68f,Color.rgb(28,91,74),22f)
            text(c,"ŚWIETNIE! Brawo! 🎉",width/2f,height*.635f,22f,mint,true,true)
            button(c,if(level>=130) "KONIEC GRY 🏆" else "NASTĘPNY POZIOM →",width*.12f,height*.71f,width*.88f,height*.79f)
        } else {
            button(c,"SPRAWDŹ RYSUNEK",width*.12f,height*.61f,width*.88f,height*.69f)
            button(c,"💡 PODPOWIEDŹ",width*.12f,height*.72f,width*.88f,height*.80f,yellow)
            if(hintShown) text(c,p.hint,width/2f,height*.84f,15f,yellow,false,true)
            text(c,"Narysuj palcem brakujący element w ramce.",width/2f,height*.90f,14f,muted,false,true)
        }
        text(c,"Wyczyść rysunek",width/2f,height*.96f,14f,muted,false,true)
    }
    private fun drawPuzzle(c: Canvas, kind: Int, cx: Float, cy: Float, s: Float, done: Boolean) {
        paint.style=Paint.Style.STROKE; paint.strokeWidth=max(4f,s*.055f); paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND; paint.color=white
        when(kind) {
            0 -> { c.drawCircle(cx,cy, s*.43f,paint); for (a in listOf(-140.0,-90.0,-40.0,0.0,40.0,140.0)) { val r=Math.toRadians(a); c.drawLine(cx+s*.5f*cos(r).toFloat(),cy+s*.5f*sin(r).toFloat(),cx+s*.68f*cos(r).toFloat(),cy+s*.68f*sin(r).toFloat(),paint) }; if(done)c.drawLine(cx+s*.5f,cy,cx+s*.72f,cy-s*.2f,paint) }
            1 -> c.drawCircle(cx,cy,s*.55f,paint)
            2 -> { c.drawRect(cx-s*.48f,cy-s*.12f,cx+s*.48f,cy+s*.5f,paint); val path=Path().apply{moveTo(cx-s*.58f,cy-s*.12f);lineTo(cx,cy-s*.62f);lineTo(cx+s*.58f,cy-s*.12f)}; c.drawPath(path,paint) }
            3,8,15 -> { c.drawLine(cx,cy+s*.45f,cx,cy-s*.25f,paint); c.drawOval(cx-s*.55f,cy-s*.45f,cx-s*.02f,cy-s*.05f,paint); c.drawOval(cx+s*.02f,cy-s*.35f,cx+s*.55f,cy+.02f,paint); if(done)c.drawOval(cx-s*.3f,cy-s*.7f,cx+s*.3f,cy-s*.35f,paint) }
            4 -> { c.drawCircle(cx,cy,s*.48f,paint); c.drawCircle(cx-s*.16f,cy-s*.1f,s*.035f,paint); c.drawCircle(cx+s*.16f,cy-s*.1f,s*.035f,paint); if(done) c.drawArc(cx-s*.2f,cy-s*.02f,cx+s*.2f,cy+s*.25f,0f,180f,false,paint) }
            5 -> { c.drawRoundRect(cx-s*.42f,cy-s*.4f,cx+s*.25f,cy+s*.45f,12f,12f,paint); c.drawCircle(cx+s*.32f,cy,s*.2f,paint) }
            6 -> { c.drawCircle(cx,cy,s*.25f,paint); c.drawLine(cx-s*.1f,cy+s*.2f,cx-s*.28f,cy+s*.48f,paint); c.drawLine(cx+s*.1f,cy+s*.2f,cx+s*.28f,cy+s*.48f,paint); c.drawLine(cx+s*.2f,cy,cx+s*.55f,cy-s*.2f,paint); c.drawCircle(cx-s*.1f,cy-s*.05f,s*.025f,paint) }
            7,13,19 -> { for(i in 0..3) c.drawLine(cx-s*.5f,cy+s*.3f-i*s*.15f,cx+s*.5f,cy+s*.3f-i*s*.15f,paint); c.drawLine(cx-s*.5f,cy+s*.3f,cx-s*.5f,cy+s*.5f,paint); c.drawLine(cx+s*.5f,cy+s*.3f,cx+s*.5f,cy+s*.5f,paint) }
            9 -> { val path=Path(); for(i in 0..10){val a=Math.PI*(i*2/10.0-0.5);val r=if(i%2==0)s*.5f else s*.23f;val x=cx+r*cos(a).toFloat();val y=cy+r*sin(a).toFloat();if(i==0)path.moveTo(x,y)else path.lineTo(x,y)};path.close();c.drawPath(path,paint) }
            10,18 -> { val path=Path().apply{moveTo(cx-s*.55f,cy); cubicTo(cx-s*.3f,cy-s*.35f,cx-s*.1f,cy+s*.35f,cx+s*.12f,cy); cubicTo(cx+s*.3f,cy-s*.35f,cx+s*.4f,cy+s*.25f,cx+s*.55f,cy)}; c.drawPath(path,paint) }
            11 -> { c.drawOval(cx-s*.52f,cy-s*.35f,cx-s*.03f,cy+s*.05f,paint); if(done)c.drawOval(cx+s*.03f,cy-s*.35f,cx+s*.52f,cy+s*.05f,paint); c.drawLine(cx,cy-s*.15f,cx,cy+s*.3f,paint) }
            12 -> { c.drawRect(cx-s*.45f,cy-s*.2f,cx+s*.45f,cy+s*.5f,paint); c.drawRect(cx+s*.12f,cy-s*.55f,cx+s*.38f,cy-s*.2f,paint) }
            14 -> { c.drawCircle(cx,cy,s*.48f,paint); paint.color=bg; paint.style=Paint.Style.FILL; c.drawCircle(cx+s*.2f,cy-s*.15f,s*.42f,paint); paint.color=white; paint.style=Paint.Style.STROKE }
            16 -> { c.drawCircle(cx-s*.32f,cy+s*.18f,s*.22f,paint); c.drawCircle(cx+s*.32f,cy+s*.18f,s*.22f,paint); c.drawLine(cx-s*.32f,cy+s*.18f,cx,cy-s*.1f,paint); c.drawLine(cx,cy-s*.1f,cx+s*.32f,cy+s*.18f,paint); c.drawLine(cx-s*.1f,cy+s*.05f,cx+s*.08f,cy+s*.05f,paint) }
            17 -> { c.drawRect(cx-s*.4f,cy-s*.5f,cx+s*.4f,cy+s*.5f,paint); if(done)c.drawCircle(cx+s*.25f,cy+s*.05f,s*.025f,paint) }
        }
        paint.style=Paint.Style.FILL
    }
    private fun drawLevels(c: Canvas) {
        text(c,"WYBIERZ POZIOM",width/2f,52f,26f,white,true,true)
        text(c,"Odblokowane: $unlocked / 130",width/2f,84f,16f,muted,false,true)
        val cols=5; val gap=10f; val cell=(width-36f-gap*(cols-1))/cols; val startY=112f
        for(i in 1..130){ val row=(i-1)/cols; val col=(i-1)%cols; val l=18f+col*(cell+gap); val t=startY+row*(cell+gap); if(t+cell>height-70)continue
            val color=if(i<=unlocked) mint else panel; round(c,l,t,l+cell,t+cell,color,12f); text(c,i.toString(),l+cell/2,t+cell*.64f,17f,if(i<=unlocked)bg else muted,true,true)
        }
        button(c,"← POWRÓT",width*.25f,height-60f,width*.75f,height-14f,yellow)
    }
    private fun drawSettings(c: Canvas) {
        text(c,"USTAWIENIA",width/2f,60f,29f,white,true,true)
        text(c,"Wybierz awatar",width/2f,130f,20f,muted,false,true)
        button(c,if(avatar==0) "👦 Chłopiec ✓" else "👦 Chłopiec",width*.12f,160f,width*.88f,220f)
        button(c,if(avatar==1) "👧 Dziewczynka ✓" else "👧 Dziewczynka",width*.12f,235f,width*.88f,295f,yellow)
        button(c,if(soundOn) "🔊 Dźwięk: włączony" else "🔇 Dźwięk: wyłączony",width*.12f,315f,width*.88f,375f,Color.rgb(105,139,255))
        text(c,"Imię można ustawić przy pierwszym uruchomieniu",width/2f,425f,14f,muted,false,true)
        button(c,"← POWRÓT",width*.2f,height*.78f,width*.8f,height*.86f)
    }
    private fun drawToast(c: Canvas, msg: String) { round(c,width*.1f,height*.48f,width*.9f,height*.55f,yellow,18f); text(c,msg,width/2f,height*.525f,16f,bg,true,true) }
    private fun splitText(s:String, maxWidth:Float, size:Float):List<String> { paint.textSize=size; val out=mutableListOf<String>(); var line=""; for(w in s.split(" ")){val n=if(line.isEmpty())w else "$line $w"; if(paint.measureText(n)>maxWidth&&line.isNotEmpty()){out.add(line);line=w}else line=n};if(line.isNotEmpty())out.add(line);return out }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x=event.x; val y=event.y
        when(screen) {
            0 -> if(event.action==MotionEvent.ACTION_UP){ when { y in height*.53f..height*.64f -> startLevel(); y in height*.65f..height*.75f -> {screen=2;invalidate()}; y in height*.76f..height*.87f -> {screen=3;invalidate()} } }
            1 -> when(event.action) {
                MotionEvent.ACTION_DOWN -> { if(y>height*.29f&&y<height*.57f&&!solved){currentPath=Path().apply{moveTo(x,y)}; invalidate();return true}; return true }
                MotionEvent.ACTION_MOVE -> { if(currentPath!=null){currentPath!!.lineTo(x,y);invalidate()};return true }
                MotionEvent.ACTION_UP -> { if(currentPath!=null){paths.add(currentPath!!);currentPath=null;strokes++;invalidate();return true}; when { y<70f&&x<100f -> {screen=0;persist();invalidate()}; solved&&y in height*.70f..height*.81f -> nextLevel(); !solved&&y in height*.60f..height*.71f -> checkDrawing(); !solved&&y in height*.71f..height*.82f -> {hintShown=true; if(coins>=5){coins-=5;showMessage="Podpowiedź za 5 monet"}else showMessage=puzzles[level-1].hint;messageUntil=System.currentTimeMillis()+2200;persist();invalidate()}; y>height*.93f -> {paths.clear();strokes=0;invalidate()} };return true }
            }
            2 -> if(event.action==MotionEvent.ACTION_UP){ if(y>height-70){screen=0}else{val cols=5;val gap=10f;val cell=(width-36f-gap*(cols-1))/cols;val col=((x-18f)/(cell+gap)).toInt();val row=((y-112f)/(cell+gap)).toInt();val pick=row*cols+col+1;if(pick in 1..unlocked){level=pick;startLevel()} };invalidate() }
            3 -> if(event.action==MotionEvent.ACTION_UP){when{y in 150f..225f->avatar=0;y in 226f..305f->avatar=1;y in 306f..390f->soundOn=!soundOn;y>height*.75f->screen=0};persist();invalidate()}
        }
        return true
    }
    private fun startLevel(){ screen=1;solved=false;hintShown=false;paths.clear();strokes=0;showMessage="";invalidate() }
    private fun checkDrawing(){
        val all=paths.flatMap { p -> val measure=pMeasure(p); measure }
        if(all.size<7){showMessage="Dorysuj coś w ramce!";messageUntil=System.currentTimeMillis()+1800;invalidate();return}
        val bounds=RectF();paths.forEach{it.computeBounds(bounds,true)}
        val area=(bounds.width()*bounds.height())/(width.toFloat()*height.toFloat())
        // Prevent a simple tap from passing; require a meaningful drawn path.
        val length=paths.sumOf { pathLength(it).toDouble() }.toFloat()
        if(length<width*.13f || area<0.0008f){showMessage="Spróbuj narysować brakujący kształt.";messageUntil=System.currentTimeMillis()+1800;invalidate();return}
        solved=true;coins+=10;unlocked=max(unlocked,min(130,level+1));if(soundOn)tone.startTone(ToneGenerator.TONE_PROP_ACK,180);showMessage="Brawo! +10 monet";messageUntil=System.currentTimeMillis()+1800;persist();invalidate()
    }
    private fun pMeasure(path:Path):List<Float>{val m=PathMeasure(path,false);val len=m.length;val n=max(1,(len/12).toInt());val out=mutableListOf<Float>();val pos=FloatArray(2);for(i in 0..n){m.getPosTan(len*i/n,pos,null);out.add(pos[0]);out.add(pos[1])};return out}
    private fun pathLength(path:Path):Float { val m=PathMeasure(path,false);var sum=0f;do{sum+=m.length}while(m.nextContour());return sum }
    private fun nextLevel(){if(level<130){level++;startLevel()}else{screen=0;showMessage="Ukończono wszystkie poziomy!";messageUntil=System.currentTimeMillis()+3000;invalidate()};persist()}
}

package cl.grabahec.app;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputFilter;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;

/**
 * GrabaHec - generador de plantillas para grabado de patentes.
 * Perfil de salida: impresora termica 58 mm / ESC-POS raster / Bluetooth SPP.
 */
public class MainActivity extends Activity {
    static final int ACCENT = Color.rgb(235, 67, 45);
    static final int BG = Color.rgb(14, 15, 16);
    static final int SURFACE = Color.rgb(28, 29, 31);
    static final int SURFACE_2 = Color.rgb(38, 40, 43);
    static final int TEXT = Color.rgb(245, 245, 245);
    static final int MUTED = Color.rgb(158, 162, 168);
    static final int WHITE = Color.WHITE;
    static final int BLACK = Color.BLACK;
    static final int PRINT_WIDTH = 384;
    static final int PREVIEW_HEIGHT = 150;
    static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    static final int TAB_DISENO = 0, TAB_MARGEN = 1, TAB_LOGO = 2, TAB_IMPRESION = 3, TAB_AJUSTES = 4;
    static final int MODE_VIDRIO = 0, MODE_PLACA = 1, MODE_MOTO = 2;

    EditText plate;
    Spinner modeSpinner, printerSpinner, surfaceSpinner;
    TextView sizeValue, connectionState;
    Switch mirror, border, chile, separator, centerLogo;
    SeekBar sizeBar, spacingBar, logoSizeBar;
    TextView title, status, validation;
    LinearLayout content;
    PreviewView preview;
    Bitmap logo;
    BluetoothAdapter bt;
    ArrayList<BluetoothDevice> printers = new ArrayList<>();
    SharedPreferences prefs;
    int tab = TAB_DISENO;
    int pendingFileRequest = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("grabahec", MODE_PRIVATE);
        loadLogo();
        build();
        requestBt();
    }

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + .5f); }

    TextView tv(String s, float sp) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(TEXT); t.setTextSize(sp);
        t.setPadding(dp(8), dp(4), dp(8), dp(4));
        return t;
    }

    Button btn(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(TEXT); b.setTextSize(15); b.setAllCaps(false);
        b.setMinHeight(dp(48));
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        return b;
    }

    GradientDrawable outline(int strokeColor, int fillColor, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillColor); g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), strokeColor);
        return g;
    }

    void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);

        // Cabecera
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL); head.setPadding(dp(14), dp(8), dp(12), dp(6));
        head.setBackgroundColor(SURFACE);
        TextView app = tv("▣  GrabaHec", 20); app.setTypeface(Typeface.DEFAULT_BOLD);
        TextView sub = tv("Plantillas · 58 mm · ESC/POS", 10); sub.setTextColor(MUTED);
        LinearLayout brand = new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL); brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(app); brand.addView(sub);
        head.addView(brand, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView badge = tv("58 mm\n384 dots", 11); badge.setGravity(Gravity.CENTER); badge.setTextColor(ACCENT);
        head.addView(badge, new LinearLayout.LayoutParams(dp(64), dp(40)));
        root.addView(head);

        // Campo patente + validación
        LinearLayout plateArea = new LinearLayout(this);
        plateArea.setOrientation(LinearLayout.VERTICAL); plateArea.setPadding(dp(14), dp(7), dp(14), dp(3));
        LinearLayout plateRow = new LinearLayout(this); plateRow.setGravity(Gravity.CENTER_VERTICAL);
        plate = new EditText(this);
        plate.setSingleLine(true); plate.setTextColor(TEXT); plate.setHintTextColor(MUTED);
        plate.setHint("ABCD12"); plate.setText(prefs.getString("plate", "ABCD12"));
        plate.setTextSize(23); plate.setGravity(Gravity.CENTER);
        plate.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        plate.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        plate.setBackground(outline(ACCENT, Color.TRANSPARENT, 10));
        plate.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { validatePlate(); refresh(); }
            public void afterTextChanged(android.text.Editable e) { saveCore(); }
        });
        plateRow.addView(plate, new LinearLayout.LayoutParams(0, dp(54), 1));
        Button clear = btn("Limpiar");
        clear.setTextSize(13); clear.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2));
        clear.setOnClickListener(v -> plate.setText(""));
        plateRow.addView(clear, new LinearLayout.LayoutParams(dp(88), dp(52)));
        plateArea.addView(plateRow);
        validation = tv("", 11); validation.setTextColor(MUTED); validation.setGravity(Gravity.CENTER);
        plateArea.addView(validation, new LinearLayout.LayoutParams(-1, dp(24)));
        root.addView(plateArea);

        // Preview siempre visible
        preview = new PreviewView(this);
        preview.setBackgroundColor(SURFACE_2);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(PREVIEW_HEIGHT)));

        title = tv("Diseño de plantilla", 18); title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(16), dp(10), dp(16), dp(8)); root.addView(title);

        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        // Navegación inferior
        LinearLayout tabs = new LinearLayout(this); tabs.setBackgroundColor(SURFACE);
        String[] names = {"Diseño", "Margen", "Logo", "Imprimir", "Ajustes"};
        for (int i = 0; i < names.length; i++) {
            final int x = i; TextView t = tv(names[i], 12); t.setGravity(Gravity.CENTER);
            t.setPadding(2, dp(10), 2, dp(10));
            t.setOnClickListener(v -> { tab = x; renderTab(); });
            tabs.addView(t, new LinearLayout.LayoutParams(0, dp(60), 1));
        }
        root.addView(tabs);
        setContentView(root);
        renderTab(); validatePlate();
    }

    void renderTab() {
        content.removeAllViews();
        String[] titles = {"Diseño de plantilla", "Margen y formato", "Logos", "Impresión 58 mm", "Ajustes"};
        title.setText(titles[tab]);
        if (tab == TAB_DISENO) designTab();
        else if (tab == TAB_MARGEN) marginTab();
        else if (tab == TAB_LOGO) logoTab();
        else if (tab == TAB_IMPRESION) printTab();
        else settingsTab();
        refresh();
    }

    TextView section(String s) {
        TextView t = tv(s.toUpperCase(Locale.ROOT), 13); t.setTextColor(MUTED);
        t.setTypeface(Typeface.DEFAULT_BOLD); t.setPadding(dp(16), dp(13), dp(16), dp(6));
        content.addView(t); return t;
    }

    LinearLayout row(View a, View b) {
        LinearLayout r = new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(16), dp(5), dp(16), dp(5)); r.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        if (b != null) r.addView(b); content.addView(r); return r;
    }

    void designTab() {
        section("Proceso de trabajo");
        modeSpinner = new Spinner(this);
        String[] modes = {"Grabado de vidrios — perfil reglamentario", "Plantilla visual — vehículo 4 ruedas", "Plantilla visual — motocicleta"};
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter); modeSpinner.setSelection(prefs.getInt("mode", MODE_VIDRIO));
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                saveCore();
                if (pos == MODE_VIDRIO && mirror != null && !mirror.isChecked()) { mirror.setChecked(true); }
                refresh();
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        row(modeSpinner, null);

        section("Superficie de grabado");
        surfaceSpinner = new Spinner(this);
        String[] surfaces = {"Vidrios: 7–10 mm", "Espejos laterales: 5–10 mm"};
        ArrayAdapter<String> sa = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, surfaces);
        surfaceSpinner.setAdapter(sa); surfaceSpinner.setSelection(prefs.getInt("surface", 0));
        surfaceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) { prefs.edit().putInt("surface", pos).apply(); updateSizeLabel(); refresh(); }
            public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        row(surfaceSpinner, null);

        section("Modo espejo");
        mirror = new Switch(this); mirror.setText("Imprimir en modo espejo"); mirror.setTextColor(TEXT);
        mirror.setChecked(prefs.getBoolean("mirror", true)); mirror.setOnCheckedChangeListener((v, c) -> { prefs.edit().putBoolean("mirror", c).apply(); refresh(); }); row(mirror, null);
        TextView mirrorInfo = tv("Se recomienda dejarlo activo cuando la plantilla se utilizará para transferencia/grabado y debe leerse correctamente desde el lado de observación previsto.", 12);
        mirrorInfo.setTextColor(MUTED); mirrorInfo.setPadding(dp(16), dp(2), dp(16), dp(10)); content.addView(mirrorInfo);

        section("Tipografía");
        TextView fontInfo = tv("GrabaHec Narrow · mayúsculas · trazado compacto · sin cursiva ni negrita en el perfil de vidrios. La vista previa muestra exactamente el raster que se envía a la impresora.", 12);
        fontInfo.setTextColor(MUTED); fontInfo.setPadding(dp(16), dp(2), dp(16), dp(12)); content.addView(fontInfo);

        Button save = btn("Guardar configuración"); save.setOnClickListener(v -> { saveCore(); toast("Configuración guardada"); });
        content.addView(save, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    void marginTab() {
        section("Formato de placa");
        border = new Switch(this); border.setText("Borde perimetral"); border.setTextColor(TEXT);
        border.setChecked(prefs.getBoolean("border", true)); border.setOnCheckedChangeListener((v,c)->{prefs.edit().putBoolean("border",c).apply();refresh();}); row(border,null);
        separator = new Switch(this); separator.setText("Separador central / punto"); separator.setTextColor(TEXT);
        separator.setChecked(prefs.getBoolean("separator", true)); separator.setOnCheckedChangeListener((v,c)->{prefs.edit().putBoolean("separator",c).apply();refresh();}); row(separator,null);
        chile = new Switch(this); chile.setText("Texto CHILE"); chile.setTextColor(TEXT);
        chile.setChecked(prefs.getBoolean("chile", false)); chile.setOnCheckedChangeListener((v,c)->{prefs.edit().putBoolean("chile",c).apply();refresh();}); row(chile,null);
        centerLogo = new Switch(this); centerLogo.setText("Logo / símbolo centrado"); centerLogo.setTextColor(TEXT);
        centerLogo.setChecked(prefs.getBoolean("centerLogo", false)); centerLogo.setOnCheckedChangeListener((v,c)->{prefs.edit().putBoolean("centerLogo",c).apply();refresh();}); row(centerLogo,null);

        section("Calibración de caracteres");
        sizeBar = new SeekBar(this); sizeBar.setMax(100); sizeBar.setProgress(prefs.getInt("size", 50));
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean f){prefs.edit().putInt("size",p).apply();updateSizeLabel();refresh();}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        sizeValue = tv("", 13); sizeValue.setTextColor(ACCENT); sizeValue.setGravity(Gravity.CENTER_HORIZONTAL); content.addView(sizeValue);
        row(labelled("Altura de caracteres", sizeBar), null);
        updateSizeLabel();
        spacingBar = new SeekBar(this); spacingBar.setMax(100); spacingBar.setProgress(prefs.getInt("spacing", 50));
        spacingBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean f){prefs.edit().putInt("spacing",p).apply();refresh();}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); row(labelled("Espaciado entre caracteres", spacingBar), null);

        TextView legal = tv("Referencia normativa: para vidrios las letras y dígitos deben medir 7–10 mm de alto; para espejos laterales, 5–10 mm. Deben ser mayúsculas y de estilo normal, sin cursiva ni negrita. Ajusta y verifica físicamente la impresión antes del grabado.", 12);
        legal.setTextColor(MUTED); legal.setPadding(dp(16), dp(12), dp(16), dp(18)); content.addView(legal);
    }

    void updateSizeLabel() {
        if (sizeValue == null) return;
        int surface = prefs.getInt("surface", 0);
        float min = surface == 0 ? 7f : 5f;
        float mm = min + (10f-min) * (prefs.getInt("size",50)/100f);
        sizeValue.setText(String.format(Locale.US, "%.1f mm de altura · %s", mm, surface == 0 ? "vidrios" : "espejos"));
    }

    LinearLayout labelled(String s, SeekBar b) {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        TextView t = tv(s, 13); t.setTextColor(MUTED); l.addView(t); l.addView(b); return l;
    }

    void logoTab() {
        section("Buscar marca");
        EditText q = new EditText(this); q.setSingleLine(true); q.setHint("Toyota, Honda, Audi..."); q.setHintTextColor(MUTED); q.setTextColor(TEXT);
        row(q, null);
        Button search = btn("Abrir Google Imágenes");
        search.setOnClickListener(v -> { String s=q.getText().toString().trim(); if(s.isEmpty()){toast("Escribe una marca");return;}
            Intent i=new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?tbm=isch&q="+Uri.encode(s+" logo png"))); startActivity(i); });
        content.addView(search,new LinearLayout.LayoutParams(-1,dp(50)));

        section("Insertar logo");
        Button paste = btn("Pegar imagen copiada"); paste.setOnClickListener(v -> pasteLogo()); content.addView(paste,new LinearLayout.LayoutParams(-1,dp(50)));
        Button file = btn("Subir desde archivo"); file.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2));
        file.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);pendingFileRequest=40;startActivityForResult(i,40);});
        content.addView(file,new LinearLayout.LayoutParams(-1,dp(50)));

        logoSizeBar = new SeekBar(this); logoSizeBar.setMax(100); logoSizeBar.setProgress(prefs.getInt("logoSize",45));
        logoSizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean f){prefs.edit().putInt("logoSize",p).apply();refresh();}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); row(labelled("Tamaño del logo",logoSizeBar),null);

        if(logo!=null){
            ImageView im=new ImageView(this); im.setImageBitmap(logo); im.setAdjustViewBounds(true); im.setPadding(dp(20),dp(15),dp(20),dp(10));
            content.addView(im,new LinearLayout.LayoutParams(-1,dp(150)));
            Button clear=btn("Quitar logo"); clear.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2));
            clear.setOnClickListener(v->{logo=null;deleteLogo();renderTab();}); content.addView(clear,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        TextView note=tv("La impresión térmica convierte el logo a blanco y negro. Usa una imagen simple, de alto contraste y con fondo limpio para obtener mejores resultados.",12);
        note.setTextColor(MUTED); note.setPadding(dp(16),dp(12),dp(16),dp(16)); content.addView(note);
    }

    void printTab() {
        section("Impresora térmica");
        status=tv("Selecciona una impresora Bluetooth emparejada.",13); status.setTextColor(MUTED); status.setPadding(dp(16),dp(5),dp(16),dp(10)); content.addView(status);
        connectionState = tv("ESC/POS · 384 dots · SPP/RFCOMM", 11); connectionState.setTextColor(ACCENT); connectionState.setPadding(dp(16),0,dp(16),dp(8)); content.addView(connectionState);
        printerSpinner=new Spinner(this); refreshPrinters(); content.addView(printerSpinner,new LinearLayout.LayoutParams(-1,dp(52)));
        Button scan=btn("Actualizar impresoras emparejadas"); scan.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2));
        scan.setOnClickListener(v->refreshPrinters()); content.addView(scan,new LinearLayout.LayoutParams(-1,dp(50)));
        Button enable=btn("Activar Bluetooth"); enable.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2));
        enable.setOnClickListener(v->{try{startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));}catch(Exception ignored){}}); content.addView(enable,new LinearLayout.LayoutParams(-1,dp(50)));
        Button print=btn("IMPRIMIR PLANTILLA"); print.setTextSize(17); print.setOnClickListener(v->printThermal()); content.addView(print,new LinearLayout.LayoutParams(-1,dp(58)));
        Button test=btn("Imprimir prueba de calibración"); test.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2)); test.setOnClickListener(v->printTest()); content.addView(test,new LinearLayout.LayoutParams(-1,dp(50)));
        Button export=btn("Guardar vista como PNG"); export.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2));
        export.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("image/png");i.putExtra(Intent.EXTRA_TITLE,"grabahec-"+plate.getText().toString()+".png");startActivityForResult(i,41);}); content.addView(export,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView help=tv("Perfil por defecto: 384 puntos de ancho, raster ESC/POS, Bluetooth SPP. Se intenta primero conexión segura y luego conexión RFCOMM no segura para ampliar compatibilidad con impresoras térmicas chinas.",12);
        help.setTextColor(MUTED); help.setPadding(dp(16),dp(14),dp(16),dp(18)); content.addView(help);
    }

    void settingsTab() {
        section("Perfil de impresión");
        TextView t=tv("58 mm nominal\n384 puntos de ancho\n203 dpi de referencia\nESC/POS raster GS v 0\nBluetooth SPP / RFCOMM\nUUID 00001101-0000-1000-8000-00805F9B34FB",13);
        t.setTextColor(MUTED); t.setPadding(dp(16),dp(8),dp(16),dp(12)); content.addView(t);
        section("Datos de la plantilla");
        TextView p=tv("Patente: "+plate.getText().toString().toUpperCase(Locale.ROOT)+"\nModo espejo: "+(prefs.getBoolean("mirror",true)?"Si":"No")+"\nLogo: "+(logo!=null?"Cargado":"No"),13);
        p.setTextColor(TEXT); p.setPadding(dp(16),dp(6),dp(16),dp(12)); content.addView(p);
        Button reset=btn("Restablecer ajustes"); reset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE_2));
        reset.setOnClickListener(v->{prefs.edit().clear().apply();logo=null;deleteLogo();plate.setText("ABCD12");toast("Ajustes restablecidos");renderTab();}); content.addView(reset,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView about=tv("GrabaHec v1.2.0\nGenerador de plantillas para grabado de patentes en formato termico 58 mm.\n\nLa app separa el proceso de grabado de vidrios del modo visual de placa, permite logos monocromos y conserva los parametros de calibracion.\n\nImportante: la vista previa es una herramienta de preparacion. La medida final depende de la impresora, densidad y medio de transferencia; realiza una prueba fisica antes de grabar.",13);
        about.setTextColor(TEXT); about.setPadding(dp(16),dp(20),dp(16),dp(24)); content.addView(about);
    }

    void validatePlate() {
        if (validation == null || plate == null) return;
        String s = cleanPlate();
        boolean ok = s.matches("[A-Z]{4}[0-9]{2}");
        if (s.isEmpty()) { validation.setText("Ingresa una patente de 6 caracteres (4 letras + 2 dígitos)"); validation.setTextColor(MUTED); }
        else if (ok) { validation.setText("Patente válida para el perfil 4 letras + 2 dígitos"); validation.setTextColor(Color.rgb(92,190,112)); }
        else { validation.setText("Formato esperado: ABCD12"); validation.setTextColor(Color.rgb(240,170,70)); }
    }

    String cleanPlate() { return plate==null?"":plate.getText().toString().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]",""); }

    void refresh() { if(preview!=null) preview.invalidate(); validatePlate(); }

    Typeface templateTypeface(boolean bold) {
        try { return Typeface.createFromAsset(getAssets(), bold ? "fonts/GrabaHecNarrowBold.ttf" : "fonts/GrabaHecNarrow.ttf"); }
        catch(Exception e) { return Typeface.create("sans-serif-condensed", bold ? Typeface.BOLD : Typeface.NORMAL); }
    }

    Bitmap renderBitmap() {
        int W=PRINT_WIDTH;
        int mode = modeSpinner==null ? prefs.getInt("mode", MODE_VIDRIO) : modeSpinner.getSelectedItemPosition();
        boolean plateMode = mode != MODE_VIDRIO;
        String s=cleanPlate(); if(s.isEmpty())s="ABCD12";
        int H = plateMode ? 190 : 145;
        Bitmap b=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b); c.drawColor(WHITE);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG); p.setColor(BLACK); p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(templateTypeface(plateMode));

        if(plateMode) {
            boolean bord = border!=null ? border.isChecked() : prefs.getBoolean("border",true);
            boolean sep = separator!=null ? separator.isChecked() : prefs.getBoolean("separator",true);
            boolean addChile = chile!=null ? chile.isChecked() : prefs.getBoolean("chile",false);
            if(bord){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawRoundRect(new RectF(8,8,W-8,H-8),8,8,p);p.setStyle(Paint.Style.FILL);}
            float fs = 68f + ((prefs.getInt("size",55)-50)*0.45f); if(mode==MODE_MOTO) fs=40f + ((prefs.getInt("size",55)-50)*0.3f); p.setTextSize(Math.max(30,fs));
            float y = mode==MODE_MOTO ? 90 : 108;
            if(sep && s.length()>=6) drawPlateGroups(c,p,s,W,y,mode==MODE_MOTO);
            else drawCenteredSpaced(c,p,s,W,y,Math.max(0,prefs.getInt("spacing",50)-35));
            if(addChile){p.setTypeface(templateTypeface(false));p.setTextSize(mode==MODE_MOTO?13:17);c.drawText("CHILE",W/2,H-22,p);}
        } else {
            // Altura física aproximada a 203 dpi: 7 mm ≈ 56 dots y 10 mm ≈ 80 dots.
            int surface = prefs.getInt("surface", 0);
            float minMm = surface == 0 ? 7f : 5f;
            float maxMm = 10f;
            float mm = minMm + (maxMm-minMm) * (prefs.getInt("size",50)/100f);
            float fs = mm * 203f / 25.4f;
            fs = Math.max(40, Math.min(82, fs));
            p.setTextSize(fs); p.setTypeface(templateTypeface(false));
            float y = 86;
            drawCenteredSpaced(c,p,s,W,y,Math.max(0,prefs.getInt("spacing",50)-45));
        }

        if(logo!=null) {
            boolean center = prefs.getBoolean("centerLogo",false);
            if(plateMode || center) drawLogo(c,W,H);
        }
        if(prefs.getBoolean("mirror",true)) {
            Matrix m=new Matrix(); m.setScale(-1,1,W/2f,0);
            Bitmap out=Bitmap.createBitmap(b,0,0,W,H,m,true); b.recycle(); b=out;
        }
        Bitmap mono=mono(b); b.recycle(); return mono;
    }

    void drawPlateGroups(Canvas c, Paint p, String s, int W, float y, boolean moto) {
        String left=s.substring(0,Math.min(2,s.length()));
        String mid=s.length()>2?s.substring(2,Math.min(4,s.length())):"";
        String right=s.length()>4?s.substring(4):"";
        float gap=moto?28:34;
        if(s.length()>=6) {
            c.drawText(left,W/2-gap*2,y,p); c.drawText(mid,W/2,y,p); c.drawText(right,W/2+gap*2,y,p);
            if(prefs.getBoolean("separator",true)) { p.setStyle(Paint.Style.FILL); c.drawCircle(W/2+gap, y-22, moto?3:5, p); }
        } else drawCenteredSpaced(c,p,s,W,y,10);
    }

    void drawCenteredSpaced(Canvas c, Paint p, String s, float center, float y, float spacing) {
        float total=0; for(int i=0;i<s.length();i++) total+=p.measureText(String.valueOf(s.charAt(i))); total+=Math.max(0,s.length()-1)*spacing;
        float x=center-total/2f;
        for(int i=0;i<s.length();i++){String ch=String.valueOf(s.charAt(i));float w=p.measureText(ch);c.drawText(ch,x+w/2,y,p);x+=w+spacing;}
    }

    void drawLogo(Canvas c,int W,int H) {
        Bitmap lb=mono(logo); int target = 20 + (prefs.getInt("logoSize",45)*40/100); float sc=Math.min((float)target/lb.getWidth(),(float)target/lb.getHeight());
        Bitmap r=Bitmap.createScaledBitmap(lb,Math.max(1,(int)(lb.getWidth()*sc)),Math.max(1,(int)(lb.getHeight()*sc)),true);
        c.drawBitmap(r,W/2-r.getWidth()/2,12,null); r.recycle(); lb.recycle();
    }

    class PreviewView extends View {
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        PreviewView(Context c){super(c);}
        protected void onDraw(Canvas c){super.onDraw(c); Bitmap b=renderBitmap(); float sc=Math.min((getWidth()-dp(24))/(float)b.getWidth(),(getHeight()-dp(14))/(float)b.getHeight()); float w=b.getWidth()*sc,h=b.getHeight()*sc; c.drawBitmap(b,null,new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2),p); b.recycle();}
    }

    Bitmap mono(Bitmap src){
        Bitmap b=src.copy(Bitmap.Config.ARGB_8888,true);
        for(int y=0;y<b.getHeight();y++) for(int x=0;x<b.getWidth();x++){
            int col=b.getPixel(x,y); int a=Color.alpha(col); int g=(Color.red(col)*30+Color.green(col)*59+Color.blue(col)*11)/100;
            b.setPixel(x,y,g<170?Color.BLACK:Color.WHITE | (a<<24));
        }
        return b;
    }

    void pasteLogo(){
        try{
            ClipboardManager cm=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(!cm.hasPrimaryClip()){toast("No hay una imagen en el portapapeles");return;}
            ClipData cd=cm.getPrimaryClip(); if(cd==null||cd.getItemCount()==0){toast("No hay una imagen en el portapapeles");return;}
            Uri u=cd.getItemAt(0).getUri(); if(u==null){toast("Copia una imagen desde Google Imágenes y vuelve a intentar");return;}
            InputStream in=getContentResolver().openInputStream(u); Bitmap bm=BitmapFactory.decodeStream(in); if(in!=null)in.close();
            if(bm==null){toast("El elemento copiado no es una imagen válida");return;} logo=bm; saveLogo(); renderTab();
        }catch(Exception e){toast("No se pudo pegar la imagen");}
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==40&&c==RESULT_OK&&d!=null){try{InputStream in=getContentResolver().openInputStream(d.getData());Bitmap bm=BitmapFactory.decodeStream(in);if(in!=null)in.close();if(bm!=null){logo=bm;saveLogo();renderTab();}}catch(Exception e){toast("No se pudo abrir la imagen");}}
        else if(r==41&&c==RESULT_OK&&d!=null){try{OutputStream out=getContentResolver().openOutputStream(d.getData());renderBitmap().compress(Bitmap.CompressFormat.PNG,100,out);if(out!=null)out.close();toast("PNG guardado");}catch(Exception e){toast("No se pudo guardar el PNG");}}
    }

    void requestBt(){
        if(Build.VERSION.SDK_INT>=31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},90);
    }

    void refreshPrinters(){
        if(printerSpinner==null)return;
        try{
            bt=BluetoothAdapter.getDefaultAdapter(); printers.clear();
            if(bt==null){status.setText("Este teléfono no dispone de Bluetooth.");return;}
            if(!bt.isEnabled()){status.setText("Bluetooth está desactivado.");}
            if(bt.isEnabled() && (Build.VERSION.SDK_INT<31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED)) printers.addAll(bt.getBondedDevices());
            ArrayList<String> names=new ArrayList<>(); for(BluetoothDevice d:printers){String n=d.getName(); if(n==null)n="Dispositivo Bluetooth";names.add(n+"\n"+d.getAddress());}
            if(names.isEmpty())names.add("No hay impresoras emparejadas");
            printerSpinner.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));
        }catch(Exception e){if(status!=null)status.setText("Activa Bluetooth y concede los permisos necesarios.");}
    }

    void printThermal(){
        try{
            if(printers.isEmpty()){toast("Empareja primero una impresora Bluetooth desde Android");return;}
            int pos=printerSpinner.getSelectedItemPosition(); if(pos<0||pos>=printers.size()){toast("Selecciona una impresora");return;}
            BluetoothDevice d=printers.get(pos);
            if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestBt();return;}
            new Thread(()->sendToPrinter(d,renderBitmap())).start();
        }catch(Exception e){toast("Error de impresión");}
    }

    void printTest(){
        if(printers.isEmpty()){toast("Empareja primero una impresora Bluetooth");return;}
        int pos=printerSpinner.getSelectedItemPosition(); if(pos<0||pos>=printers.size()){toast("Selecciona una impresora");return;}
        Bitmap test=Bitmap.createBitmap(PRINT_WIDTH,100,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(test);c.drawColor(WHITE);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(BLACK);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(templateTypeface(false));p.setTextSize(34);c.drawText("GRABAHEC 58 mm",PRINT_WIDTH/2,45,p);p.setTextSize(18);c.drawText("384 DOTS  •  PRUEBA",PRINT_WIDTH/2,75,p);Bitmap printable=mono(test); test.recycle(); new Thread(()->sendToPrinter(printers.get(pos),printable)).start();}

    void sendToPrinter(BluetoothDevice d, Bitmap image){
        BluetoothSocket s=null; try{
            if(Build.VERSION.SDK_INT>=31&&checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){runOnUiThread(()->toast("Falta permiso Bluetooth"));return;}
            try{s=d.createRfcommSocketToServiceRecord(SPP_UUID);s.connect();}
            catch(Exception secure){try{if(s!=null)s.close();}catch(Exception ignored){} s=d.createInsecureRfcommSocketToServiceRecord(SPP_UUID);s.connect();}
            OutputStream o=s.getOutputStream();o.write(new byte[]{0x1B,0x40});o.write(escRaster(image));o.write(new byte[]{0x1B,0x64,0x03});o.flush();Thread.sleep(120);o.close();s.close();
            runOnUiThread(()->toast("Plantilla enviada a la impresora"));
        }catch(Exception e){try{if(s!=null)s.close();}catch(Exception ignored){} String msg=e.getMessage()==null?"revisa que la impresora use Bluetooth SPP":e.getMessage();runOnUiThread(()->toast("No se pudo imprimir: "+msg));}
    }

    byte[] escRaster(Bitmap b)throws IOException{
        int w=b.getWidth(),h=b.getHeight(),wb=(w+7)/8;ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(0x1D);out.write(0x76);out.write(0x30);out.write(0);out.write(wb&255);out.write((wb>>8)&255);out.write(h&255);out.write((h>>8)&255);
        for(int y=0;y<h;y++)for(int x=0;x<wb;x++){int val=0;for(int bit=0;bit<8;bit++){int xx=x*8+bit;if(xx<w&&Color.red(b.getPixel(xx,y))<128)val|=(1<<(7-bit));}out.write(val);}return out.toByteArray();
    }

    void saveCore(){
        if(plate==null)return; SharedPreferences.Editor e=prefs.edit();e.putString("plate",cleanPlate());if(mirror!=null)e.putBoolean("mirror",mirror.isChecked()); else e.putBoolean("mirror",prefs.getBoolean("mirror",true));
        if(modeSpinner!=null)e.putInt("mode",modeSpinner.getSelectedItemPosition());e.apply();
    }

    void saveLogo(){try{File f=new File(getFilesDir(),"logo.png");FileOutputStream o=new FileOutputStream(f);logo.compress(Bitmap.CompressFormat.PNG,100,o);o.close();}catch(Exception ignored){}}
    void loadLogo(){try{File f=new File(getFilesDir(),"logo.png");if(f.exists())logo=BitmapFactory.decodeFile(f.getAbsolutePath());}catch(Exception ignored){}}
    void deleteLogo(){try{new File(getFilesDir(),"logo.png").delete();}catch(Exception ignored){}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
}

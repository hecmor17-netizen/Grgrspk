# GrabaHec v1.2.0 — Android

Aplicacion para preparar plantillas de grabado de placa patente en impresoras termicas de 58 mm.

## Funciones

- Perfil de grabado de vidrios y espejos con rangos de altura configurables.
- Salida espejo para transferencia.
- Vista previa raster a 384 puntos de ancho.
- Tipografia compacta incluida en `app/src/main/assets/fonts/`.
- Insercion de logos desde archivo o portapapeles.
- Conversión de logos a monocromo para impresion termica.
- Bluetooth SPP/RFCOMM para impresoras ESC/POS compatibles.
- Prueba de calibracion y exportacion PNG.
- Pestañas separadas: Diseno, Margen, Logo, Imprimir y Ajustes.
- Persistencia de patente, logo y calibracion.

## Generar el APK

### Opcion A — Android Studio

1. Abre la carpeta `GrabaHec` en Android Studio.
2. Espera la sincronizacion de Gradle.
3. Selecciona `Build > Build APK(s)`.
4. El APK debug quedara en `app/build/outputs/apk/debug/app-debug.apk`.

### Opcion B — GitHub Actions, sin instalar Android Studio

1. Crea un repositorio nuevo en GitHub.
2. Sube el contenido de esta carpeta.
3. Ve a **Actions** y ejecuta **Build GrabaHec APK**.
4. Descarga el artefacto `GrabaHec-v1.2.0-debug`.

El workflow instala Android SDK y Gradle automaticamente.

## Impresoras

La app usa ESC/POS raster y el servicio Bluetooth SPP/RFCOMM clasico. El soporte depende de que la impresora implemente ese perfil. En impresoras chinas, primero debe estar emparejada desde Android.

## Medidas normativas

El perfil de vidrios incorpora como referencia los rangos del reglamento chileno publicado en 2024: 7–10 mm para vidrios y 5–10 mm para espejos laterales, con mayusculas y estilo normal, sin cursiva ni negrita.

La medida fisica final puede variar por densidad, mecanismo de la impresora, papel y medio de transferencia. Se debe realizar una prueba de calibracion antes de un grabado definitivo.

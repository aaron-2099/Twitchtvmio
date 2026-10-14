# TwitchUnblock para Android TV — pasos (sin saber programar)

## 1. Compilar la APK (gratis, desde el navegador)
1. Crea una cuenta en https://github.com (si no tienes).
2. Arriba a la derecha: **+ → New repository**. Ponle de nombre `TwitchUnblock-TV`, déjalo **Public** y pulsa **Create repository**.
3. Descomprime este ZIP en tu PC. En el repositorio nuevo pulsa **uploading an existing file** y arrastra TODO el contenido de la carpeta (incluida la carpeta oculta `.github`). Pulsa **Commit changes**.
   - Si no te deja subir `.github`: en el repo pulsa **Add file → Create new file**, escribe como nombre `.github/workflows/build.yml` y pega dentro el contenido de ese archivo.
4. Abre la pestaña **Actions**. Verás "Build APK" ejecutándose. Tarda unos 3-5 minutos. Si sale un check verde, está listo.

## 2. Descargar la APK
En la página principal del repo, columna derecha → **Releases → TwitchUnblock TV (última versión)** → descarga `TwitchUnblock-TV.apk`.

## 3. Instalar en la TV
- Opción fácil: instala la app **Downloader** en la TV, y escribe la dirección de descarga del archivo (clic derecho en el enlace de Releases → copiar dirección).
- Opción USB: copia la APK a un pendrive y ábrela desde un explorador de archivos en la TV.
- En ambos casos, la TV te pedirá permitir "instalar apps de origen desconocido" para esa app. Acepta.

## Manejo con el mando
- Flechas: moverse entre botones. OK: pulsar. Atrás: volver.
- Botones multimedia (play/pausa, adelantar, rebobinar) controlan el vídeo.

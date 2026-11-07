# Yoinks Android

[English](README.md) · **Português**

Uma iniciativa Android baseada no [Yoinks de Pablo Stanley](https://github.com/pablostanley/yoinks)
("yoink any video from your terminal"). Leva a mesma ideia para o celular:
baixa um vídeo MP4 ou extrai um áudio MP3 com yt-dlp e FFmpeg, tudo no
aparelho. Também salva as imagens de posts de fotos do TikTok.

Este é um projeto independente, sem afiliação ou endosso do autor do Yoinks
original.

Consulte a [especificação funcional e técnica](ESPECIFICACAO.md).

## Baixar

Baixe o APK na [última release](https://github.com/lucasjr76/yoinks_android/releases/latest).
Ele é ARM64 e requer Android 10 ou superior.

A partir da 0.4.1, os APKs são assinados com a chave de release do projeto
(certificado `CN=lucasjr76`), e as próximas versões instalam por cima. As
versões até a 0.4.0 usavam chave de depuração: desinstale-as uma vez antes de
instalar a 0.4.1.

## Usar

1. Abra o Yoinks Android, copie um link HTTPS e toque em **Colar link**, ou use
   **Compartilhar → Yoinks Android** no navegador ou no app de origem.
2. Toque em **Baixar vídeo MP4** ou **Baixar áudio MP3**.
3. Aguarde com o app aberto. O arquivo fica em **Downloads/Yoinks**.
4. Em um post de fotos do TikTok, o botão de vídeo vira **Baixar fotos (JPG)**
   e salva as imagens do post.

Um download por vez, qualidade automática e interface em português. A
cobertura de sites depende da versão embutida do yt-dlp.

## Compilar

Requisitos: JDK 17, Android SDK 35 e Gradle 8.9 (ou o wrapper incluído).
No Android Studio, abra esta pasta e sincronize o projeto. Pela linha de comando:

```sh
./gradlew :app:assembleDebug                  # ARM64 + x86_64 (emulador)
./gradlew :app:assembleDebug -Pabi=arm64-v8a  # APK só para celular
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Testes de unidade: `./gradlew :app:testDebugUnitTest`.

### Release assinada

Os APKs publicados são assinados com a chave de release do mantenedor, guardada
fora do repositório em `~/.config/yoinks-android/` (`release.jks` e
`keystore.properties`). Com esses arquivos no lugar:

```sh
./gradlew :app:assembleRelease -Pabi=arm64-v8a
# app/build/outputs/apk/release/app-release.apk
```

Use `-PyoinksKeystore=/caminho/keystore.properties` para outro local. Sem a
chave, a build de release sai sem assinatura; use a build de depuração.

## Motor yt-dlp

O recurso `app/src/main/res/raw/ytdlp` substitui a cópia padrão da biblioteca
pela versão portátil [2026.10.26](https://github.com/yt-dlp/yt-dlp/releases/tag/2026.10.26).
O SHA-256 do arquivo corresponde ao publicado na release:

```text
e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3
```

## Licenças

Código sob [GPL-3.0-only](LICENSE). O aviso MIT do Yoinks original está em
[`LICENSE-YOINKS`](LICENSE-YOINKS). O motor Android é o
[youtubedl-android](https://github.com/yausername/youtubedl-android), distribuído
sob GPL-3.0, e incorpora yt-dlp e FFmpeg. O código-fonte de cada APK publicado
é a tag correspondente neste repositório.

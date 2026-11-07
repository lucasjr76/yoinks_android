# Yoinks Android
## Especificação funcional e técnica · v0.4.1

[English](SPECIFICATION.md) · **Português**

### Objetivo

Baixar mídia a partir de um link compartilhado ou colado no Android. O usuário
escolhe entre vídeo MP4 e áudio MP3. O arquivo fica disponível no gerenciador
de arquivos em **Downloads/Yoinks**. O processamento ocorre no aparelho.

### Requisitos funcionais

| ID | Requisito | Comportamento esperado |
| --- | --- | --- |
| RF-01 | Entrada manual e pela área de transferência | O campo aceita digitação e colagem padrão. **Colar link** preenche o campo com o primeiro URL HTTP ou HTTPS reconhecido no texto. |
| RF-02 | Compartilhamento do Android | A ação `ACTION_SEND` com MIME `text/plain` abre o app ou encaminha o link para a tela já aberta. O usuário escolhe o formato para iniciar. |
| RF-03 | Vídeo | **Baixar vídeo MP4** seleciona automaticamente vídeo H.264 em MP4 e áudio M4A, quando disponíveis, com alternativa de um fluxo MP4 combinado. |
| RF-04 | Áudio | **Baixar áudio MP3** baixa a melhor faixa de áudio disponível e usa FFmpeg para convertê-la para MP3. |
| RF-05 | Download em andamento | Exibe preparação, progresso quando informado pelo motor e salvamento. Os dois botões de download ficam indisponíveis até a conclusão da operação. |
| RF-06 | Arquivo final | Publica a mídia em Downloads/Yoinks e informa o nome registrado pelo Android. |
| RF-07 | Erros | Indica link inválido no campo. Para erros do motor ou de gravação, exibe o diagnóstico e permite nova tentativa. |
| RF-08 | Posts de fotos do TikTok | Para links `tiktok.com/@…/photo/…`, o botão de vídeo vira **Baixar fotos (JPG)** e salva cada imagem do post em Downloads/Yoinks. **Baixar áudio MP3** salva a música do post. |
| RF-09 | Menu ⋮ | **Verificar atualizações** consulta a última release no GitHub e só avisa quando há versão nova, com link para a página da release. **Sobre** mostra versão, autoria ([lucasjr76](https://github.com/lucasjr76/)), link do repositório e crédito ao Yoinks original. |

### Interface

A tela usa fundo escuro, destaques verde-menta e três áreas para link, formato
e acompanhamento. O formulário é rolável para acomodar telas pequenas, teclado
e fontes ampliadas. O estado do download tem mensagens textuais e a barra de
progresso aparece durante a operação. O ícone vetorial adaptativo usa uma seta
de download e inclui uma camada monocromática para launchers compatíveis.

### Implementação

| Componente | Implementação |
| --- | --- |
| App | Java 17, Activity e layouts XML nativos. Pacote `br.com.develox.yoinks`. |
| Android | API mínima 29, alvo 34, compilação com SDK 35. O APK ARM64 é o pacote de teste para celular. O projeto Gradle também inclui x86_64. |
| Motor | `youtubedl-android 0.18.1` com yt-dlp 2026.10.26, runtime Python 3.12 e FFmpeg empacotados. |
| Execução | Executor de uma thread por tela. O download ocorre em diretório temporário no cache do app. |
| Publicação | `MediaStore.Downloads`, com registro pendente durante a cópia. Em caso de erro durante a publicação, remove o registro criado. |
| Permissões | `INTERNET` para o download. A publicação pelo MediaStore dispensa acesso amplo ao armazenamento. |

### Limites desta versão

A operação foi projetada para um download por vez, com o app aberto. A tela
oferece seleção automática de qualidade. A cobertura de sites depende dos
extratores do yt-dlp incluído e pode variar conforme a plataforma. As fotos
do TikTok são lidas diretamente da página do post, sem o yt-dlp, e dependem
da estrutura atual dessa página. Links curtos `vm.tiktok.com` de posts de
fotos ainda não são suportados. Reprodução local pelo próprio Yoinks é a
próxima etapa prevista.

### Critérios de aceite no aparelho

1. Ver o novo ícone no launcher e abrir a tela com os controles legíveis.
2. Copiar um link válido e tocar em **Colar link**. Conferir que o campo é
   preenchido e aguarda a seleção do formato.
3. Compartilhar o link pelo navegador com **Yoinks Android** e repetir com o
   app já aberto. Conferir o preenchimento do campo nos dois casos.
4. Baixar MP4 e MP3 e abrir os dois arquivos a partir de **Downloads/Yoinks**.
5. Tentar iniciar com o campo vazio e com uma URL indisponível. Conferir o erro
   apresentado e realizar um download válido em seguida.
6. Usar a tela em paisagem e com fonte ampliada. Conferir que os botões, o campo
   e o estado do download seguem acessíveis pela rolagem.

### Build e licenças

Consulte o [README](README.pt-BR.md) para compilar e instalar. O código deste
protótipo é GPL-3.0-only. Este app é uma iniciativa derivada do
[Yoinks de Pablo Stanley](https://github.com/pablostanley/yoinks), cujo aviso
MIT está em
[`LICENSE-YOINKS`](LICENSE-YOINKS).

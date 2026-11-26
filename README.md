# SLMs on Device — app Android

App de demostración para el proyecto **"SLMs on Device"** (UNAL, Ingeniería de Sistemas,
Desarrollo de Aplicaciones para Dispositivos Móviles). Ejecuta un *Small Language Model* (SLM)
**completamente en el teléfono**: no hay servidor ni API en la nube. Internet solo se usa para
descargar el modelo; después el chat funciona en **modo avión**.

- Runtime: **LiteRT-LM** (Google AI Edge), AAR precompilado `com.google.ai.edge.litertlm:litertlm-android:0.19.0`
  (incluye las librerías nativas para `arm64-v8a` y `x86_64`; no requiere NDK).
- Modelos: archivos `.litertlm` de [litert-community](https://huggingface.co/litert-community) en Hugging Face.
- UI: Kotlin + Jetpack Compose (Material 3, tema oscuro).

## Funcionalidades

| Pantalla | Qué hace |
|---|---|
| **Chat** | Respuesta en *streaming* token a token, botón detener, nueva conversación, indicador de red ("Sin red · 100 % local"), métricas por respuesta. |
| **Modelos** | Catálogo (parámetros, cuantización, tamaño, licencia), descarga con progreso **reanudable**, borrar, **importar `.litertlm`** desde archivo (SAF), detecta modelos copiados con `adb push`. |
| **Ajustes** | Backend CPU/GPU, temperatura, top-K, top-P, máx. tokens de salida, prompt de sistema, modo razonamiento (Qwen3), token de Hugging Face opcional. |

### Métricas mostradas

| Métrica | Cómo se obtiene |
|---|---|
| Tiempo de carga | Reloj de pared alrededor de `Engine.initialize()` (se muestra en la cabecera del chat). |
| TTFT (*time to first token*) | Desde que se envía el mensaje hasta el primer fragmento recibido (latencia percibida: incluye el *prefill* del prompt). |
| tok/s | Velocidad de **decodificación** reportada por el runtime (`BenchmarkInfo.lastDecodeTokensPerSecond`). |
| tokens / prompt tok | Conteos exactos del runtime (`lastDecodeTokenCount`, `lastPrefillTokenCount`). |

Los conteos exactos vienen de la API experimental de *benchmark* de LiteRT-LM
(`ExperimentalFlags.enableBenchmark = true`). Si el runtime no los entrega, la app los **estima**
y los muestra con **"≈"**: tokens ≈ caracteres / 4 (heurística típica de tokenizadores BPE), y
tok/s ≈ (tokens − 1) / (tiempo desde el primer token hasta el final).

## Arquitectura

```
┌──────────────────────────── UI (Jetpack Compose) ────────────────────────────┐
│  MainActivity ── navegación por pestañas (estado, sin librería de navegación) │
│     ├── ui/ChatScreen.kt      chat + streaming + métricas + indicador de red  │
│     ├── ui/ModelsScreen.kt    catálogo, descarga, importación (SAF), borrar   │
│     └── ui/SettingsScreen.kt  backend y parámetros de muestreo                │
└───────────────────────────────────────┬───────────────────────────────────────┘
                                        │ StateFlow<UiState>
                          ┌─────────────▼─────────────┐
                          │    ui/ChatViewModel.kt    │  viewModelScope, libera el
                          │  (estado de toda la app)  │  motor en onCleared()
                          └──────┬─────────────┬──────┘
                                 │             │
              ┌──────────────────▼───┐   ┌─────▼──────────────────────────┐
              │ data/ModelRepository │   │ llm/LlmEngine.kt               │
              │  descarga HTTP Range │   │  load()  → Engine.initialize() │
              │  importación SAF     │   │  generate() → Flow<Event>      │
              │  archivos .litertlm  │   │  stop()  → cancelProcess()     │
              ├──────────────────────┤   │  (Dispatchers.Default)         │
              │ data/ModelCatalog    │   └─────┬──────────────────────────┘
              │ data/SettingsStore   │         │ JNI
              └──────────────────────┘   ┌─────▼──────────────────────────┐
                                         │ LiteRT-LM (C++) · XNNPACK CPU  │
                                         │               · OpenCL GPU     │
                                         └────────────────────────────────┘
```

Archivos principales (`app/src/main/java/com/edu/unal/haspima/slmsondevice/`):

- `data/ModelCatalog.kt` — lista de modelos con URL, tamaño, cuantización y licencia.
- `data/ModelRepository.kt` — descarga (reanudable, valida tamaño y cabecera `LITERTLM`), importación, borrado.
- `data/SettingsStore.kt` — ajustes de inferencia en `SharedPreferences`.
- `llm/LlmEngine.kt` — envoltura delgada de LiteRT-LM (cargar / generar como `Flow` / detener / liberar).
- `llm/Metrics.kt` — cálculo puro de métricas (probado con tests unitarios).
- `ui/ChatViewModel.kt`, `ui/ChatScreen.kt`, `ui/ModelsScreen.kt`, `ui/SettingsScreen.kt`.

El núcleo de la inferencia, simplificado:

```kotlin
val engine = Engine(EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(), cacheDir = cacheDir.path))
engine.initialize()                                  // bloqueante: fuera del hilo principal
val conversation = engine.createConversation(
    ConversationConfig(systemInstruction = Contents.of("Eres un asistente…"),
                       samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7)))
conversation.sendMessageAsync("Hola").collect { print(it) }   // Flow<Message>, un fragmento por evento
```

## Compilar

Requisitos: Android SDK (platform 37), JDK 17+ (Gradle descarga el JDK 25 de su *daemon* vía foojay).

```bash
cd android-app
./gradlew assembleDebug          # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # tests unitarios (métricas, catálogo, nombres de archivo)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> Nota: el AAR de LiteRT-LM 0.19.0 está compilado con Kotlin 2.4, por eso el proyecto usa
> Kotlin **2.4.0** (la plantilla traía 2.2.10, que no puede leer esos metadatos).

## Cómo obtener los modelos

Tamaños verificados con `curl -sIL` (noviembre 2026):

| Modelo | Archivo | Tamaño | Cuantización | Licencia |
|---|---|---:|---|---|
| Qwen3 0.6B (por defecto) | `Qwen3-0.6B.litertlm` | 614 MB | INT8 dinámico | Apache 2.0 |
| Qwen3 0.6B INT4 | `Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm` | 343 MB | INT4 bloque-32 | Apache 2.0 |
| Qwen2.5 1.5B Instruct | `Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm` | 1.60 GB | INT8 | Apache 2.0 |
| Gemma 3 1B IT (restringido) | `gemma3-1b-it-int4.litertlm` | 584 MB | INT4 | Gemma Terms of Use |

1. **Descarga en la app** (pestaña *Modelos* → *Descargar*). Si se interrumpe, *Reanudar* continúa
   desde el archivo `.part` con la cabecera HTTP `Range`.
2. **`adb push`** (útil en clase, sin depender del wifi):
   ```bash
   curl -L -O https://huggingface.co/litert-community/Qwen3-0.6B/resolve/main/Qwen3-0.6B.litertlm
   adb shell mkdir -p /sdcard/Android/data/com.edu.unal.haspima.slmsondevice/files/models
   adb push Qwen3-0.6B.litertlm /sdcard/Android/data/com.edu.unal.haspima.slmsondevice/files/models/
   ```
   Luego en *Modelos* pulse *Actualizar*.
3. **Importar desde archivo** (*Importar modelo (.litertlm)*): abre el selector del sistema (Storage
   Access Framework) y copia el archivo a la carpeta de modelos. Es la vía recomendada para
   **Gemma 3**, cuyo repositorio exige iniciar sesión en Hugging Face y aceptar la licencia.
   Alternativa: pegar un token de lectura de Hugging Face en *Ajustes* y descargarlo en la app.

## Rendimiento

Referencias publicadas en la ficha del modelo (litert-community, medidas con `litert_lm_advanced_main`,
256 tokens de prompt / 256 de salida) para `Qwen3-0.6B.litertlm`:

| Dispositivo | Backend | Prefill (tok/s) | Decode (tok/s) | TTFT |
|---|---|---:|---:|---:|
| Samsung SM-S937U1 | GPU OpenCL | 646 | 25.3 | 0.44 s |
| Samsung SM-S937U1 | CPU | 212 | 13.0 | 1.28 s |
| TECNO LJ9 (gama media) | CPU | 95 | 9.3 | 2.8 s |

Para la variante INT4 en GPU la misma ficha reporta ~70–80 tok/s en gama alta. Memoria pico
aproximada: 2.7 GB en CPU para el modelo INT8, por lo que se recomiendan teléfonos con ≥ 6 GB de RAM.

Prueba de humo realizada en un **emulador x86_64** (Android 37, 4 GB de RAM, sin aceleración):
`Qwen3-0.6B INT4` cargó en ~20–30 s y generó a ~1 tok/s (TTFT ≈ 71 s, 28 tokens, prompt 37 tokens).
El emulador está limitado por memoria; **estas cifras no representan un teléfono real**.
Complete esta tabla con sus mediciones en dispositivo físico:

| Teléfono | Modelo | Backend | Carga | TTFT | tok/s |
|---|---|---|---:|---:|---:|
| _(por medir)_ | Qwen3 0.6B INT8 | CPU | | | |
| _(por medir)_ | Qwen3 0.6B INT8 | GPU | | | |

## Limitaciones

- La **primera carga** de un modelo es lenta (decenas de segundos en gama media); `cacheDir` acelera las siguientes.
- **GPU**: depende de que el fabricante exponga OpenCL (`libOpenCL.so`). Si falla, la app vuelve a CPU y lo informa.
  El backend NPU no se expone (requiere modelos específicos por chip).
- Al **detener** una respuesta, LiteRT-LM no revierte el estado interno de la conversación; si el
  contexto queda raro, use *Nueva conversación*.
- Los ajustes de muestreo y el prompt de sistema se aplican al crear la conversación: guardarlos reinicia el chat.
- El historial no se guarda entre sesiones. Ventana de contexto limitada (4096 tokens en los modelos del catálogo).
- Modelos pequeños (0.6 B) alucinan y razonan poco; son una demostración de viabilidad, no de calidad.
- Solo texto (LiteRT-LM soporta imagen/audio con otros modelos, no implementado aquí).
# SLMs_on_Device

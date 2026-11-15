# TV Lite RCA

Una pantalla de inicio liviana para el **Smart TV RCA 40D2900**
(chasis MediaTek **MT5655**, firmware `V8-T56FT05-LF1V299`).

Sale de [TV Lite](https://github.com/maurocartagironi/tvlite), el launcher que
se hizo para un BGH con 935 MB de RAM. La idea es la misma: no es un launcher
bonito, es un launcher que entra. Lo que cambia es el televisor.

## Por qué no sirve TV Lite tal cual

| | TV Lite (BGH) | Este (RCA) |
| --- | --- | --- |
| Android | 14, Google TV | viejo y desconocido, launcher propio de fábrica |
| `minSdk` | 31 (Android 12) | **19 (Android 4.4)** |
| Android TV (`leanback`) | obligatorio | **opcional**: si no, el RCA rechaza la instalación |
| Apps que lista | solo las de Android TV, con banner | **todas**: las de TV con banner y las comunes con ícono y nombre |
| Disposición | una fila | **grilla**: estos televisores traen muchas apps de fábrica |
| Fondo | fotos que se cargan por `adb` | color plano: acá no hay `adb` |
| Clima | METAR de Aeroparque | no: cero red, cero trabajo de fondo |
| Fecha | `java.time` (Android 8+) | `SimpleDateFormat` |

Se mantienen las decisiones que hacen a TV Lite liviano:

- **Cero dependencias.** Ni AndroidX, ni Compose, ni AppCompat. El bloque
  `dependencies` está vacío.
- **Pantalla armada en código**, sin inflar XML.
- **Cada tarjeta es una sola View** que se dibuja entera.
- **Los dibujos se rasterizan al tamaño exacto de la tarjeta**, ahora en
  `RGB_565` (la mitad de memoria que en TV Lite).
- **El reloj escucha `ACTION_TIME_TICK`**: no hay temporizadores.
- **Sin animaciones** de ventana ni de foco: en este chip se ven a saltos.

## Lo que agrega

- **Versión de Android y memoria libre** arriba a la derecha, actualizadas cada minuto. En el RCA la versión no aparece en ningún menú. El problema
  de estos televisores es la memoria, no el procesador, y así se ve.
- **Tarjeta "Liberar memoria"**: cierra lo que quedó abierto en segundo plano
  y muestra cuántos MB se recuperaron. Solo toca procesos que el sistema ya
  considera prescindibles.
- **Mantener apretado OK** (o la tecla **Menú** del control) sobre una app:
  - *Ocultar de esta pantalla*, para sacar las apps de fábrica que nadie usa.
  - *Información*: la pantalla del sistema para **borrar caché, forzar
    detención o desinstalar**, a dos clics.
- **Tarjeta "Mostrar ocultas"** cuando hay alguna oculta.
- **Tarjeta "Ajustes"**, siempre. Es parte del plan para volver atrás.

## Instalar en el televisor (sin computadora)

1. **Bajar el APK.** En [Releases](../../releases/tag/ultima) está
   `tvlite-rca.apk`, que GitHub compila solo en cada cambio. Descargalo en la
   compu o el celular y copialo a un **pendrive**.
2. **Permitir la instalación.** En el televisor: Ajustes → Seguridad (o
   Restricciones) → activar **Orígenes desconocidos**.
3. **Instalar.** Enchufá el pendrive, abrí el explorador de archivos o el
   reproductor multimedia del televisor, buscá `tvlite-rca.apk` y abrilo.
4. **Probarlo.** Abrilo desde la lista de apps (se llama *TV Lite*) y verificá
   que ande bien con el control.
5. **Hacerlo la pantalla de inicio.** Apretá **Inicio** en el control. Al haber
   dos launchers, el televisor debería preguntar cuál usar: elegí *TV Lite* y
   **Siempre**.

Si el televisor no pregunta, puede ser que el fabricante haya bloqueado el
cambio de launcher. En ese caso TV Lite igual se puede usar abriéndolo desde
la lista de apps, pero el launcher de fábrica sigue cargado por detrás y la
mejora es menor.

## Cómo volver atrás

Desde la tarjeta **Ajustes** de esta misma pantalla: Aplicaciones → TV Lite →
**Borrar valores predeterminados** (vuelve a preguntar qué inicio usar) o
**Desinstalar** (vuelve el de fábrica directamente).

Por eso la tarjeta de Ajustes está siempre, aunque falle todo lo demás, y por
eso el arranque está envuelto en `runCatching`: si algo sale mal aparece una
pantalla mínima con un botón a Ajustes. Una pantalla de inicio que se cae deja
al televisor sin ninguna.

## Otras cosas que ayudan en este televisor

- **Desenchufarlo un minuto** de vez en cuando. Apagarlo con el control lo
  deja en espera y no libera nada. Si existe "Encendido rápido", desactivarlo.
- **Dejar al menos 20 % de almacenamiento libre.**
- **Opciones de desarrollador** (Ajustes → Acerca de → 7 toques en *Número de
  compilación*), si el fabricante las dejó: las tres escalas de animación en
  0,5x o desactivadas, y *Límite de procesos en segundo plano* en 2 o 3.
- **Kodi y Chrome** son lo más pesado que puede correr acá. Usarlos lo menos
  posible, y en Kodi desactivar las actualizaciones automáticas de complementos.

## Compilar

GitHub lo hace solo (`.github/workflows/apk.yml`). A mano:

```sh
./gradlew assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

El release se firma con la clave de depuración a propósito: se instala a mano
y nunca va a Play.

## Pendiente

- Saber qué versión de Android tiene el televisor; si es 5.0 o más, se puede
  subir `minSdk` y simplificar.
- Medir el consumo real contra el launcher de fábrica (necesita `adb`).

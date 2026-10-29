# Pulso · Asistencia Odoo

Aplicación Android en español para Odoo Community 18: inicio de sesión, empleado vinculado,
entrada/salida, calendario semanal con descansos y listado de las últimas 100 asistencias.
Interfaz Compose Material 3, colores azul y turquesa, confirmaciones y mensajes de conexión.

## Abrir y actualizar en Android Studio (Windows)

1. Abre este proyecto y usa **Git → Pull**, remoto `origin`, rama `master`.
2. Selecciona JDK **17 o 21** en Settings → Build Tools → Gradle → Gradle JDK.
3. Sincroniza Gradle. Instala Android SDK Platform **35** y Build Tools **35.0.0** si lo pide.
4. Selecciona un emulador (Android 7/API 24 o superior) y pulsa Run.

Herramientas: AGP 8.9.1, Gradle 8.11.1 con checksum oficial, Kotlin 2.1.20 y Compose BOM 2025.04.01.
No reutiliza las herramientas preliminares del proyecto original ni requiere Java 25.
Si aparece `Connection reset`, revisa la conexión/proxy de Android Studio y vuelve a sincronizar;
no desactives TLS ni cambies checksums. En Settings → HTTP Proxy usa la configuración que requiera tu red.

## Odoo en Ubuntu VMware

Desde el checkout existente, sin reinstalar ni crear otra copia:

```bash
sudo systemctl start postgresql
cd ~/odoo18
./.venv/bin/python odoo-bin --db_user=alexander --http-interface=0.0.0.0 --addons-path=addons,custom_addons
```

Deja esa terminal abierta. `hostname -I` muestra la IP actual. Primero comprueba desde Windows
que abre `http://IP-DE-UBUNTU:8069`. La app trae como ejemplo `http://192.168.245.132:8069`,
base `odoo_pruebas`; ambos son editables. Usa el usuario y contraseña de **Odoo**, no los de Ubuntu
ni la contraseña maestra de bases de datos. No hay contraseñas incluidas en el proyecto.

La app usa la API JSON-RPC estándar de Odoo, con cookie de sesión en memoria. No necesita
modificar el módulo del curso. Busca el empleado a partir del usuario autenticado: no fija los
IDs de usuario ni empleado. Lee `hr.attendance` y consulta `resource.calendar.attendance`. El fichaje del empleado usa el controlador oficial de Odoo con la hora del servidor. El administrador solo consulta el equipo.
Instala Empleados/Asistencias, vincula el empleado a tu usuario y configura su horario.
El servidor aplica sus ACL y reglas de registros: la app no otorga permisos ni usa `sudo`.
El administrador utilizado en la práctica puede hacer estas operaciones; para otros usuarios,
el administrador debe revisar sus permisos. La app pide resolver vínculos ambiguos entre empresas.

HTTP está habilitado para esta práctica en la red local de VMware. No publiques ese servicio en
Internet; un despliegue real necesita HTTPS. El emulador debe poder alcanzar la IP de VMware;
la prueba desde Windows no garantiza la ruta de un teléfono físico. No requiere CORS.

## Sesiones y registros

- Contraseña y cookie no se persisten; vuelve a iniciar sesión al cerrar el proceso de la app.
- Inicio muestra el estado y el total de horas de los registros cerrados consultados, no un total mensual.
- Horario distingue descansos, días libres y semanas alternas cuando están definidas.
- Historial convierte UTC a la zona del dispositivo; el horario conserva la hora del calendario Odoo.
- Confirma entrada/salida, serializa operaciones y no reintenta escrituras automáticamente.
- Si falla una operación, actualiza el estado antes de repetirla para evitar duplicados.
- El fichaje del empleado usa la hora de Odoo. El administrador no registra marcas ni modifica asistencias desde la app.

## APK y comprobaciones

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug --max-workers=2
```

En Windows usa `gradlew.bat`. APK local: `app/build/outputs/apk/debug/app-debug.apk`.
Cada push a `master`/`main` ejecuta GitHub Actions; una ejecución correcta publica el artefacto
**Pulso-debug-apk** en Actions → Android APK → ejecución → Artifacts. Es un APK de pruebas,
no una publicación de Play Store. Descargar o actualizar Git no instala automáticamente la app:
usa Run en Android Studio o instala el APK en el dispositivo de pruebas.

Prueba funcional pendiente en tu red: iniciar sesión, verificar Alexander, consultar horario,
registrar entrada, actualizar Odoo, registrar salida y comprobar historial. Incluye pruebas
unitarias de JSON-RPC con un servidor simulado, cookies, permisos denegados, fechas UTC,
vínculo de empleado y prevención de entradas duplicadas. Las pruebas simuladas no reemplazan
la comprobación contra tu Ubuntu.

### Validación realizada en el entorno de desarrollo

- `assembleDebug`: correcto; APK con paquete `com.example.asistenciaodoo`, API mínima 24, destino 35 y permiso de Internet.
- `testDebugUnitTest`: 5 pruebas, 0 fallos, 0 errores y 0 omitidas.
- `lintDebug`: 0 errores y 19 advertencias no bloqueantes (principalmente recursos de la plantilla sin uso).
- No se ejecutó un emulador ni se accedió al Odoo de VMware desde este entorno; la prueba funcional en tu red sigue pendiente.

## Empleado y Administrador

La versión 1.2 abre directamente Equipo para el Administrador, que solo consulta todas las asistencias permitidas. El empleado conserva su fichaje, horario e historial.
Consulta [ROLES.md](ROLES.md) para configurar dos cuentas, conocer los permisos y hacer la demostración.
No se elige el rol en el login: lo determina el servidor. Las pruebas de la versión anterior
indicadas arriba corresponden a la entrega inicial; la nueva suite incluye los casos de roles.

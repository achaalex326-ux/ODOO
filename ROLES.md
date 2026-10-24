# Roles de Pulso

El rol se consulta en Odoo después de autenticar la sesión. No hay un selector de rol en el
login ni contraseñas especiales de administrador. La pertenencia al grupo
`hr_attendance.group_hr_attendance_manager` determina Administrador; el resto usa Empleado.
Las funciones administrativas vuelven a consultar el grupo antes de leer o modificar datos.
Las ACL y reglas de registros de Odoo, incluidas las de empresa, siguen aplicándose.

| Función | Empleado | Administrador |
| --- | --- | --- |
| Consultar su horario e historial | Sí | Sí |
| Registrar su entrada y salida | Sí | Sí |
| Ver panel Equipo | No | Sí |
| Consultar las últimas 100 asistencias del equipo permitido | No | Sí |
| Cerrar una asistencia abierta de otro empleado, con confirmación | No | Sí |
| Asignar roles desde la app | No | No: se asignan en Odoo |

## Configurar dos cuentas para demostrarlo

1. En Odoo entra con la cuenta administradora. Abre **Ajustes → Usuarios y empresas → Usuarios**.
2. Abre la cuenta de Alexander. En permisos de **Asistencias**, asigna **Administrador** si
   quieres que sea la cuenta administrativa. Guarda. No basta con tener permisos de Ajustes.
3. Crea una segunda cuenta **interna**, con un identificador distinto y una contraseña propia.
   No la hagas portal, administrador de Ajustes, oficial de Asistencias ni administrador de Asistencias.
   Revisa todos los permisos: algunas bases copian permisos administrativos a nuevos usuarios.
4. En Empleados crea una ficha diferente para la segunda cuenta. En Ajustes → Usuario relacionado,
   vincula exclusivamente esa cuenta. Asigna un horario de trabajo.
5. La configuración de la empresa debe permitir la asistencia desde la barra de Odoo
   (Asistencias → Configuración → Ajustes → asistencia desde la barra / systray).
6. Inicia sesión en Pulso con cada cuenta por separado. El rol se muestra en Inicio y Ajustes.
   La cuenta Administrador tiene además la pestaña **Equipo**.

La cuenta de empleado obtiene por ser interna el grupo nativo de lectura de sus asistencias.
No se le deben conceder permisos administrativos solo para permitir fichar: el fichaje propio
usa `/hr_attendance/systray_check_in_out`, que elige el empleado de la sesión y la hora del servidor.
El administrador cierra asistencias mediante `hr.attendance.write`, con comprobación del grupo
y las reglas nativas. No se añaden reglas amplias ni permisos por `sudo` en el cliente.

La API de ejemplo del curso no se usa para el panel del equipo ni para el fichaje de esta versión.
Esta actualización no modifica los controladores antiguos instalados en Ubuntu; conviene auditar
por separado esos endpoints de ejemplo antes de exponer Odoo fuera de la práctica local.

## Prueba de presentación

- Empleado: rol visible, cuatro pestañas, horario propio, entrada/salida propia e historial propio.
- Administrador: rol visible y pestaña Equipo. Actualiza y comprueba nombres y asistencias.
- Con una asistencia de pruebas abierta, pulsa Registrar salida del empleado y confirma.
- Comprueba el mismo registro en Odoo. No cierres una jornada real para demostrar la función.
- Quita el rol administrativo en Odoo y pulsa Actualizar en Equipo: debe denegarse. Al volver a
  iniciar sesión desaparecerá la pestaña.

Las pruebas de servidor simulado comprueban el rol, la denegación al empleado, la revocación,
el cierre administrativo y la no repetición de un cierre. Debe verificarse también con las
cuentas y permisos reales de tu Ubuntu, al que este entorno no tiene acceso de red.

## Validación de esta actualización

APK 1.1 generado correctamente. Suite: 9 pruebas aprobadas, 0 fallos y 0 omitidas.
`lintDebug` terminó sin errores. No se ha probado aún con las dos cuentas reales de Ubuntu.

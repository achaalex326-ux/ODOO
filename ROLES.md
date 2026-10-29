# Roles de Pulso 1.2

El rol se consulta en Odoo después de autenticar. No hay selector de rol ni contraseñas especiales.
El grupo `hr_attendance.group_hr_attendance_manager` determina Administrador; los otros usuarios
internos usan Empleado. Cada consulta administrativa vuelve a validar el grupo en Odoo.

| Función en Pulso | Empleado | Administrador |
| --- | --- | --- |
| Pantalla inicial | Mi jornada | Equipo |
| Registrar entrada y salida propias | Sí | No |
| Consultar horario e historial propios | Sí | No |
| Consultar todas las asistencias del equipo autorizado | No | Sí |
| Modificar o cerrar asistencias del equipo | No | No |
| Cambiar roles | No | No; se configuran en Odoo |

El administrador tiene únicamente Equipo y Ajustes. Su panel muestra nombre del empleado,
entrada, salida, horas de jornadas cerradas y estado de jornada abierta. Carga todos los registros
por páginas de 200, sin el anterior límite de 100, y respeta las empresas y reglas de Odoo.
No necesita una ficha de empleado vinculada para consultar el panel.

El empleado conserva Inicio, Horario, Historial y Ajustes. Su historial muestra los últimos 100
registros propios. El fichaje usa la función oficial de Odoo, que determina el empleado de la
sesión y usa la hora del servidor. No acepta un ID de empleado elegido desde la app.

## Configurar dos cuentas para presentar

1. Conserva la cuenta actual con Asistencias → Administrador en Ajustes → Usuarios y empresas → Usuarios.
2. Para José Miguel Godoy usa una cuenta interna, sin Asistencias Administrador/Oficial ni Administración Ajustes.
   Revisa los valores predeterminados antes de guardar; no copies permisos administrativos.
3. Establece su contraseña desde el engranaje → Cambiar contraseña. No la subas a GitHub.
4. Crea una ficha de empleado para José y vincúlala en Ajustes → Usuario relacionado. Asigna horario y empresa.
5. En configuración de Asistencias de la empresa, permite asistencia desde la barra de Odoo (systray).
6. Actualiza la app con Git → Pull, ejecuta y cierra la sesión anterior.

## Demostración

- Entra con José: debe indicar Empleado, sin Equipo. Consulta el horario, marca entrada y luego salida.
- Cierra sesión y entra como administrador: debe abrir Equipo, sin Registrar entrada ni Registrar salida.
- Pulsa Actualizar: comprueba la asistencia de José, incluyendo sus horas.
- Verifica el mismo registro en Odoo. Las cuentas de prueba deben pertenecer a empresas accesibles al administrador.

Este modo de consulta se aplica a la app. No modifica los privilegios administrativos de la cuenta
cuando se usa la interfaz web de Odoo. La API de ejemplo del curso tampoco se modifica ni se utiliza
para consultar Equipo; sus endpoints antiguos requieren una auditoría aparte antes de exponerlos fuera
de la red de prácticas.

## Pruebas

La suite comprueba sesión, empleado, fichaje propio, cookies, UTC, roles, revocación, administrador
sin ficha de empleado, bloqueo de marcas administrativas, navegación por rol y lectura de más de
una página de asistencias. La prueba con las dos cuentas reales debe realizarse en tu red de VMware.

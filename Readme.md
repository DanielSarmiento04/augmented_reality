
<h1 align="center">
   Aplicación de Realidad Aumentada
</h1>

<center>
    Jose Daniel Sarmiento , Manuel Ayala  | { jose2192232, jose2195529 } @correo.uis.edu.co
</center>

## Resumen

Este es un proyecto de ejemplo para la creación de una aplicación de realidad aumentada utilizando Jetpack Compose y la arquitectura MVVM (Modelo-Vista-ViewModel) en Android. El objetivo principal de este proyecto es integrar capacidades de realidad aumentada (AR) para facilitar el mantenimiento industrial, proporcionando una interfaz de usuario moderna y eficiente utilizando las últimas tecnologías de Android.


## Objetivos del Proyecto

1. Realidad Aumentada (AR): Desarrollar una aplicación que aproveche la realidad aumentada para superponer información en el entorno físico del usuario, enfocada en casos de uso industriales, como el mantenimiento de equipos.
2. Arquitectura MVVM: Separar las responsabilidades de lógica empresarial, visualización y manejo de datos utilizando el patrón de arquitectura MVVM.
3. Jetpack Compose: Implementar la interfaz de usuario utilizando Jetpack Compose, el nuevo enfoque declarativo de UI en Android.
4. Navegación entre pantallas: Permitir la navegación entre pantallas clave (por ejemplo, Inicio de sesión y Contenido del usuario).
5. Soporte de múltiples temas: Implementar un sistema de temas para ofrecer soporte para modo oscuro y claro.

## Tecnologías Utilizadas

- Jetpack Compose: La librería moderna de Android para construir interfaces de usuario de manera declarativa.
- MVVM (Modelo-Vista-ViewModel): Arquitectura que ayuda a separar las capas de presentación, negocio y datos.
- Kotlin Coroutines: Para manejar operaciones asíncronas de forma eficiente.
- Navegación en Jetpack Compose: Para gestionar las transiciones entre pantallas.
- Hilt (opcional): Inyección de dependencias simplificada.
- Realidad Aumentada (ARCore): Para integrar la funcionalidad de realidad aumentada.


## Estructura del Proyecto

```bash
/app
├── /src
│   ├── /main
│   │   ├── /java/com/example/augmented_reality
│   │   │   ├── MainActivity.kt
│   │   │   ├── /ui
│   │   │   │   ├── LoginView.kt
│   │   │   │   ├── UserContentView.kt
│   │   │   ├── /viewmodel
│   │   │   │   ├── UserViewModel.kt
│   │   │   ├── /model
│   │   │   │   ├── User.kt
│   │   │   ├── /services
│   │   │   │   ├── AuthorizationService.kt
│   │   │   │   ├── AuthenticationService.kt
```

## Funcionalidades

- **Inicio de sesión con Autenticación y Autorización**
  - Soporta credenciales estáticas por defecto (`username: Daniel`, `password: Contravene`).
  - Permite credenciales personalizadas.
  - Maneja autenticación basada en tokens y verifica a los usuarios con una API.

- **Manejo de Errores**
  - Muestra mensajes de error apropiados cuando el inicio de sesión o la autenticación fallan.
  - Proporciona la opción de reintentar el inicio de sesión si falla.

- **Indicador de Carga**
  - Muestra un indicador de carga cuando el proceso de inicio de sesión o autenticación está en curso.
  - Deshabilita las acciones de inicio de sesión mientras el estado de carga está activo.

## Requisitos

- Android Studio Flamingo (o superior)
- Conocimientos básicos de Android y Kotlin
- ARCore para funcionalidades de realidad aumentada (para versiones futuras)

## Instalación

1. Clona el repositorio:

```
    git clone https://github.com/DanielSarmiento04/augmented_reality
```

2. Abre el proyecto en Android Studio.
3. Compila y ejecuta el proyecto en un dispositivo o emulador compatible con ARCore.

## Próximos Pasos

- Integración de ARCore: Agregar funcionalidad de realidad aumentada para superponer datos industriales sobre objetos físicos.
- Autenticación Real: Conectar el login a una base de datos o API de autenticación.
- Mejora de la UI: Añadir animaciones y mejorar la experiencia de usuario con más elementos interactivos.

## Contribuciones

Este es un proyecto abierto a mejoras y colaboraciones. Si deseas contribuir, puedes hacerlo mediante pull requests o abriendo issues en el repositorio.
Licencia

## Licencia

Este proyecto no debe ser utilizado para fines de lucro

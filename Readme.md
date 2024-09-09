# Aplicación de Realidad Aumentada

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
Navegación en Jetpack Compose: Para gestionar las transiciones entre pantallas.
- Hilt (opcional): Inyección de dependencias simplificada.
Realidad Aumentada (ARCore): Para integrar la funcionalidad de realidad aumentada.
Estructura del Proyecto

```bash
/app
│
├── /src
│   ├── /main
│   │   ├── /java/com/example/augmented_reality
│   │   │   ├── MainActivity.kt               // Actividad principal con la navegación
│   │   │   ├── /ui
│   │   │   │   ├── LoginView.kt              // Pantalla de inicio de sesión
│   │   │   │   ├── UserContentView.kt        // Pantalla de contenido del usuario
│   │   │   │   ├── /theme                    // Archivos de temas
│   │   │   ├── /model
│   │   │   │   ├── User.kt                   // Modelo de datos del usuario
│   │   │   ├── /viewmodel
│   │   │   │   ├── UserViewModel.kt          // ViewModel para gestionar el estado del usuario
│   ├── AndroidManifest.xml                    // Configuración del manifiesto de Android
```

## Funcionalidades

- Inicio de Sesión: Pantalla donde los usuarios pueden iniciar sesión. Actualmente, utiliza un login simulado que se puede expandir para autenticar usuarios con servicios en la nube o bases de datos.
Pantalla de Contenido del Usuario: Una vez autenticado, el usuario accede a esta pantalla, donde puede ver información personalizada.
- Navegación: Navegación entre pantallas utilizando Jetpack Compose Navigation.
Temas Claros y Oscuros: Soporte para temas dinámicos basados en las preferencias del sistema.
Requisitos

Android Studio Flamingo (o superior)
Conocimientos básicos de Android y Kotlin
ARCore para funcionalidades de realidad aumentada (para versiones futuras)
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


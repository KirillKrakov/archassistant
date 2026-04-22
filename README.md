# ArchAssistant Backend

Система автоматического контроля архитектурных стандартов при генерации кода на Java/Kotlin.

## Быстрый старт

### Требования
- Java 17+
- Docker и Docker Compose
- Gradle 8.0+

### Запуск через Docker Compose

```bash
# Сборка и запуск всех сервисов
docker-compose up -d --build

# Проверка статуса
docker-compose ps

# Просмотр логов
docker-compose logs -f backend
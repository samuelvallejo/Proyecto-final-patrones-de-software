"""Derive the table catalog and complete relation diagram from the actual migration."""
from pathlib import Path
import re,json
root=Path(__file__).resolve().parent.parent
ddl=(root/'backend/src/main/resources/db/migration/V1__platform.sql').read_text(encoding='utf-8')
tables=re.findall(r'CREATE TABLE (\w+)\s*\((.*?)\);',ddl,re.S)
extensions=set('user_devices tags stream_tags message_reactions message_reports moderation_rules sanction_appeals clip_tags clip_exports clip_shares audience_reactions channel_webhooks webhook_deliveries channel_subscriptions user_badges emotes channel_emotes playlists playlist_items'.split())
catalogs=set('roles categories ai_providers ai_models subscription_plans badges'.split())
descriptions={
 'users':'Identidad, correo, credenciales y estado de la cuenta',
 'user_profiles':'Nombre público, biografía e idioma',
 'roles':'Catálogo de roles globales',
 'user_roles':'Roles asociados a cada usuario',
 'auth_sessions':'Hash de tokens y vencimiento de sesiones',
 'user_devices':'Inventario futuro de dispositivos',
 'user_consents':'Consentimiento versionado para el análisis del chat',
 'channels':'Canales y propiedad de cada comunidad',
 'channel_settings':'Modo lento, enlaces, idioma y clips automáticos',
 'channel_moderators':'Permisos de moderación específicos por canal',
 'channel_follows':'Seguimiento de canales por usuarios',
 'categories':'Categorías de transmisiones',
 'tags':'Etiquetas reutilizables para contenido',
 'streams':'Título, categoría, inicio, fin y estado del directo',
 'stream_tags':'Etiquetas asociadas a transmisiones',
 'stream_viewer_sessions':'Ingreso y salida de espectadores conectados',
 'stream_events':'Acontecimientos del directo con payload JSONB',
 'chat_rooms':'Sala única para cada transmisión',
 'chat_messages':'Mensajes persistentes y estado de moderación',
 'message_reactions':'Reacciones a mensajes individuales',
 'message_reports':'Reportes realizados por espectadores',
 'moderation_policies':'Niveles, umbrales y medidas automáticas',
 'blocked_words':'Restricciones explícitas de palabras por canal',
 'blocked_topics':'Temas enviados como contexto a Gemini',
 'moderation_rules':'Extensión para acciones específicas por categoría',
 'ai_providers':'Catálogo de proveedores de IA',
 'ai_models':'Catálogo de modelos y capacidades',
 'ai_requests':'Tarea, entrada JSONB, modelo solicitado y estado',
 'ai_responses':'Respuesta estructurada y latencia de cada solicitud',
 'message_classifications':'Categoría, riesgo, razón y proveedor por mensaje',
 'moderation_queue':'Casos pendientes y decisión del revisor humano',
 'moderation_actions':'Historial de medidas y actor humano o automático',
 'user_sanctions':'Silencios/bloqueos por canal con expiración y revocación',
 'sanction_appeals':'Extensión para apelaciones de sanciones',
 'user_warnings':'Advertencias resultantes de moderación automática',
 'media_assets':'Metadatos de archivos reales en el volumen persistente',
 'recording_segments':'Intervalos de grabación asociados a archivos',
 'stream_highlights':'Marcadores manuales, aumentos del chat y audio',
 'clips':'Clip, video, intervalo, metadatos y estado de publicación',
 'clip_versions':'Historial previo a ediciones y recortes',
 'clip_tags':'Etiquetas asignadas a clips',
 'clip_reviews':'Decisiones del propietario sobre publicación',
 'clip_exports':'Extensión para trabajos de exportación por formato',
 'clip_shares':'Extensión para registrar destinos compartidos',
 'stream_summaries':'Resúmenes vinculados a su solicitud de IA',
 'stream_topics':'Temas identificados en el contexto del directo',
 'transcripts':'Transcripciones y su origen',
 'subtitle_cues':'Fragmentos de texto con tiempos de inicio y fin',
 'chat_faqs':'Preguntas y respuestas identificadas por el asistente',
 'stream_analytics':'Muestras por minuto de audiencia y moderación',
 'audience_reactions':'Extensión para reacciones de audiencia al directo',
 'notifications':'Avisos privados de moderación y clips',
 'notification_preferences':'Preferencias inicializadas para futuras entregas configurables',
 'audit_logs':'Auditoría de configuración del canal',
 'channel_webhooks':'Extensión para integraciones externas por evento',
 'webhook_deliveries':'Extensión para intentos y estado de entregas',
 'subscription_plans':'Catálogo de planes de comunidad',
 'channel_subscriptions':'Extensión para suscripciones de usuarios a canales',
 'badges':'Catálogo de insignias',
 'user_badges':'Extensión para asignación de insignias',
 'emotes':'Extensión para recursos y códigos de emotes',
 'channel_emotes':'Extensión para emotes disponibles por canal',
 'playlists':'Extensión para listas públicas o privadas del canal',
 'playlist_items':'Extensión para clips ordenados dentro de listas',
}
assert len(tables)==64 and set(descriptions)=={name for name,_ in tables}
relations=[]
for name,body in tables:
 for target in re.findall(r'REFERENCES (\w+)\(',body):relations.append((target,name))
lines=['# Base de datos PostgreSQL','',f'El esquema contiene **{len(tables)} tablas de dominio**. Flyway agrega `flyway_schema_history`, que no se cuenta para el requisito académico. El catálogo y las relaciones se generan desde la migración real con `scripts/document-schema.py`.','',
'## Integridad y diseño','',
'El modelo usa UUID, claves primarias, relaciones mediante claves foráneas, restricciones únicas, estados acotados mediante CHECK, tiempos TIMESTAMPTZ y entradas/respuestas de IA en JSONB. Un índice parcial impide dos directos activos del mismo canal. Otro evita duplicar clips para un marcador. Las listas relacionadas usan claves compuestas. Hay índices de búsqueda para salas, cola, sanciones, clips e historial, y un índice GIN para payloads de eventos.','',
'Los permisos se comprueban por canal en servicios del backend. Los hashes de contraseñas y sesiones permanecen en PostgreSQL. Los videos se guardan en un volumen, relacionados mediante `media_assets`; no se almacenan como grandes binarios dentro de las tablas.','',
'## Catálogo y alcance','',
'**Operativa:** usada por un flujo implementado. **Catálogo:** precargada para clasificar/configurar el dominio. **Extensión:** modelo persistente preparado, sin una función completa de usuario en esta versión. Las tablas de exportación y compartidos son extensiones de seguimiento: la descarga y el enlace de compartir sí funcionan a través del archivo y el clip, sin escribir en esas tablas.','',
'| # | Tabla | Finalidad | Alcance |','|---|---|---|---|']
for i,(name,body) in enumerate(tables,1):
 scope='Extensión' if name in extensions else 'Catálogo' if name in catalogs else 'Operativa'
 lines.append(f'| {i} | `{name}` | {descriptions[name]} | {scope} |')
lines+=['','## Verificar el requisito','',"```sql\nSELECT count(*) AS tablas_de_dominio\nFROM information_schema.tables\nWHERE table_schema = 'public'\n  AND table_type = 'BASE TABLE'\n  AND table_name <> 'flyway_schema_history';\n-- Resultado esperado: 64\n```",'',
'## Recorrido principal','',
'`users` posee `channels`; un canal define política y moderadores y tiene `streams`. Cada directo tiene `chat_rooms` y `chat_messages`. Un mensaje puede tener clasificación, solicitud/respuesta de IA, caso de cola y acciones. Las acciones generan advertencias o sanciones. Los momentos del directo relacionan grabaciones, archivos y clips, y los clips conservan versiones y decisiones de revisión. Los resúmenes y subtítulos aportan contexto editorial y las muestras de audiencia alimentan el panel.','',
'## Diagrama completo de relaciones','',
'El diagrama siguiente incluye las 64 tablas y las relaciones declaradas. Los detalles de columnas, nulabilidad, claves compuestas y restricciones están en las migraciones SQL. La cardinalidad uno-a-muchos se usa como vista general de claves foráneas; las restricciones UNIQUE/PK del SQL precisan las relaciones uno-a-uno.','',
'```mermaid','erDiagram']
for name,body in tables:
 first=re.match(r'\s*(\w+)\s+([A-Z]+)',body)
 lines.append(f'    {name} {{\n        {first.group(2)} {first.group(1)}\n    }}')
for parent,child in sorted(set(relations)):lines.append(f'    {parent} ||--o{{ {child} : references')
lines+=['```','',
'## Migraciones','',
'- `V1__platform.sql`: esquema, índices y catálogos iniciales.\n- `V2__clip_uniqueness.sql`: unicidad de clip por marcador e índice de segmentos.','',
'No edites una migración ya aplicada en producción. Agrega una nueva versión para cambiar el esquema. En Railway, el backend ejecuta las migraciones al arrancar sobre la base privada.']
(root/'docs/BASE_DE_DATOS.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
(root/'docs/schema.json').write_text(json.dumps({'tables':len(tables),'foreignKeys':len(relations),'catalog':[{'name':name,'purpose':descriptions[name],'scope':'extension' if name in extensions else 'catalog' if name in catalogs else 'operational'} for name,_ in tables]},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Documented {len(tables)} tables and {len(relations)} foreign-key references.')

"""Derive the table catalog and complete relation diagram from the actual migration."""
from pathlib import Path
import re,json
root=Path(__file__).resolve().parent.parent
copy=json.loads((root/'scripts/locales/schema.es.json').read_text(encoding='utf-8'))
ddl=(root/'backend/src/main/resources/db/migration/V1__platform.sql').read_text(encoding='utf-8')
tables=re.findall(r'CREATE TABLE (\w+)\s*\((.*?)\);',ddl,re.S)
extensions=set('user_devices tags stream_tags message_reactions message_reports moderation_rules sanction_appeals clip_tags clip_exports clip_shares audience_reactions channel_webhooks webhook_deliveries channel_subscriptions user_badges emotes channel_emotes playlists playlist_items'.split())
catalogs=set('roles categories ai_providers ai_models subscription_plans badges'.split())
descriptions={
 'users':copy["schemaText01"],
 'user_profiles':copy["schemaText02"],
 'roles':copy["schemaText03"],
 'user_roles':copy["schemaText04"],
 'auth_sessions':copy["schemaText05"],
 'user_devices':copy["schemaText06"],
 'user_consents':copy["schemaText07"],
 'channels':copy["schemaText08"],
 'channel_settings':copy["schemaText09"],
 'channel_moderators':copy["schemaText10"],
 'channel_follows':copy["schemaText11"],
 'categories':copy["schemaText12"],
 'tags':copy["schemaText13"],
 'streams':copy["schemaText14"],
 'stream_tags':copy["schemaText15"],
 'stream_viewer_sessions':copy["schemaText16"],
 'stream_events':copy["schemaText17"],
 'chat_rooms':copy["schemaText18"],
 'chat_messages':copy["schemaText19"],
 'message_reactions':copy["schemaLabelText01"],
 'message_reports':copy["schemaText20"],
 'moderation_policies':copy["schemaText21"],
 'blocked_words':copy["schemaText22"],
 'blocked_topics':copy["schemaLabelText02"],
 'moderation_rules':copy["schemaText23"],
 'ai_providers':copy["schemaText24"],
 'ai_models':copy["schemaText25"],
 'ai_requests':copy["schemaLabelText03"],
 'ai_responses':copy["schemaText26"],
 'message_classifications':copy["schemaText27"],
 'moderation_queue':copy["schemaText28"],
 'moderation_actions':copy["schemaText29"],
 'user_sanctions':copy["schemaText30"],
 'sanction_appeals':copy["schemaText31"],
 'user_warnings':copy["schemaText32"],
 'media_assets':copy["schemaText33"],
 'recording_segments':copy["schemaText34"],
 'stream_highlights':copy["schemaText35"],
 'clips':copy["schemaText36"],
 'clip_versions':copy["schemaLabelText04"],
 'clip_tags':copy["schemaLabelText05"],
 'clip_reviews':copy["schemaText37"],
 'clip_exports':copy["schemaText38"],
 'clip_shares':copy["schemaText39"],
 'stream_summaries':copy["schemaText40"],
 'stream_topics':copy["schemaText41"],
 'transcripts':copy["schemaLabelText06"],
 'subtitle_cues':copy["schemaText42"],
 'chat_faqs':copy["schemaText43"],
 'stream_analytics':copy["schemaText44"],
 'audience_reactions':copy["schemaText45"],
 'notifications':copy["schemaText46"],
 'notification_preferences':copy["schemaText47"],
 'audit_logs':copy["schemaText48"],
 'channel_webhooks':copy["schemaText49"],
 'webhook_deliveries':copy["schemaText50"],
 'subscription_plans':copy["schemaText51"],
 'channel_subscriptions':copy["schemaText52"],
 'badges':copy["schemaText53"],
 'user_badges':copy["schemaText54"],
 'emotes':copy["schemaText55"],
 'channel_emotes':copy["schemaText56"],
 'playlists':copy["schemaText57"],
 'playlist_items':copy["schemaText58"],
}
assert len(tables)==64 and set(descriptions)=={name for name,_ in tables}
relations=[]
for name,body in tables:
 for target in re.findall(r'REFERENCES (\w+)\(',body):relations.append((target,name))
lines=[copy["schemaText59"],'',copy["schemaIntroduction"].format(count=len(tables)),'',
copy["schemaText60"],'',
copy["schemaText61"],'',
copy["schemaText62"],'',
copy["schemaText63"],'',
copy["schemaText64"],'',
copy["schemaLabelText07"],'|---|---|---|---|']
for i,(name,body) in enumerate(tables,1):
 scope=copy["schemaText65"] if name in extensions else copy["schemaText66"] if name in catalogs else copy["schemaLabelText08"]
 lines.append(f'| {i} | `{name}` | {descriptions[name]} | {scope} |')
lines+=['',copy["schemaText67"],'',copy["schemaLabelText11"],'',
copy["schemaLabelText09"],'',
copy["schemaText68"],'',
copy["schemaText69"],'',
copy["schemaText70"],'',
'```mermaid','erDiagram']
for name,body in tables:
 first=re.match(r'\s*(\w+)\s+([A-Z]+)',body)
 lines.append(f'    {name} {{\n        {first.group(2)} {first.group(1)}\n    }}')
for parent,child in sorted(set(relations)):lines.append(f'    {parent} ||--o{{ {child} : references')
lines+=['```','',
copy["schemaLabelText10"],'',
copy["schemaText71"],'',
copy["schemaText72"]]
(root/'docs/BASE_DE_DATOS.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
(root/'docs/schema.json').write_text(json.dumps({'tables':len(tables),'foreignKeys':len(relations),'catalog':[{'name':name,'purpose':descriptions[name],'scope':'extension' if name in extensions else 'catalog' if name in catalogs else 'operational'} for name,_ in tables]},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Documented {len(tables)} tables and {len(relations)} foreign-key references.')

import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const migrations = ['V1__platform.sql', 'V2__clip_uniqueness.sql'];
const sources = migrations.map(name => fs.readFileSync(path.join(root, 'backend/src/main/resources/db/migration', name), 'utf8'));
const output = path.join(root, 'deploy/supabase');
fs.mkdirSync(output, {recursive: true});
const protection = `
DO $$
DECLARE item record;
BEGIN
  FOR item IN SELECT tablename FROM pg_tables WHERE schemaname = 'streamguard' LOOP
    EXECUTE format('ALTER TABLE streamguard.%I ENABLE ROW LEVEL SECURITY', item.tablename);
    EXECUTE format('REVOKE ALL ON TABLE streamguard.%I FROM PUBLIC, anon, authenticated', item.tablename);
  END LOOP;
END $$;
REVOKE ALL ON SCHEMA streamguard FROM PUBLIC, anon, authenticated;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA streamguard FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA streamguard REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated;
ALTER DEFAULT PRIVILEGES IN SCHEMA streamguard REVOKE ALL ON SEQUENCES FROM PUBLIC, anon, authenticated;
`;
const sql = '-- Generated from immutable Flyway V1/V2. Apply once to a new project only.\n'
  + 'CREATE SCHEMA streamguard AUTHORIZATION postgres;\nSET LOCAL search_path TO streamguard, pg_catalog;\n'
  + sources.join('\n') + '\n' + protection;
fs.writeFileSync(path.join(output, 'bootstrap.sql'), sql);
fs.writeFileSync(path.join(output, 'snapshot.json'), JSON.stringify({version: 2, schema: 'streamguard', domainTables: 64,
  migrations: migrations.map((name, index) => ({name, sha256: createHash('sha256').update(sources[index]).digest('hex')}))}, null, 2) + '\n');
console.log('Prepared the private Supabase schema and V1/V2 snapshot.');

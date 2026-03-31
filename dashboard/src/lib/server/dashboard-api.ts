import fs from 'fs';
import path from 'path';

import type { InspectorMessage } from '$lib/inspector';

import { exec, query } from './clickhouse';

export type DashboardMetrics = {
	totalEvents: number;
	activeDevices: number;
	storageBytes: number;
	alerts24h: number;
};

export type DeviceRecord = {
	id: string;
	location: string;
	sensors: string;
	status: string;
	updatedAt: string;
};

export type SettingsRecord = {
	currentTtlDays: number;
	s3: {
		endpoint: string;
		region: string;
		access_key: string;
		secret_key: string;
	};
};

const TTL_COLUMNS: Record<string, string> = {
	rawframes: 'ts',
	session_features: 'inserted_at',
	inference_results: 'created_at',
	inference_alerts: 'created_at',
	env_features: 'inserted_at',
	power_features: 'inserted_at'
};

function escapeSql(value: string): string {
	return value.replace(/'/g, "''");
}

function clampLimit(value: string | null | undefined, fallback: number, max = 200): number {
	const parsed = Number.parseInt(value ?? '', 10);
	if (Number.isNaN(parsed)) return fallback;
	return Math.max(1, Math.min(parsed, max));
}

export function formatBytes(sizeBytes: number): string {
	if (!Number.isFinite(sizeBytes) || sizeBytes <= 0) return '0 B';
	const units = ['B', 'KB', 'MB', 'GB'];
	let size = sizeBytes;
	let unit = 0;
	while (size >= 1024 && unit < units.length - 1) {
		size /= 1024;
		unit += 1;
	}
	return `${size.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}

export async function getDashboardMetrics(): Promise<DashboardMetrics> {
	const [totalRes, deviceRes, storageRes, alertRes] = await Promise.all([
		query('SELECT count() AS count FROM brain.rawframes'),
		query("SELECT count(DISTINCT id) AS count FROM brain.devices WHERE lower(status) = 'online'"),
		query("SELECT sum(data_compressed_bytes) AS bytes FROM system.parts WHERE database = 'brain'"),
		query('SELECT count() AS count FROM brain.inference_alerts WHERE created_at > now() - INTERVAL 24 HOUR')
	]);

	return {
		totalEvents: Number(totalRes.data[0]?.count || 0),
		activeDevices: Number(deviceRes.data[0]?.count || 0),
		storageBytes: Number(storageRes.data[0]?.bytes || 0),
		alerts24h: Number(alertRes.data[0]?.count || 0)
	};
}

export async function getThroughputCount(): Promise<number> {
	const res = await query(`
		SELECT count() AS count
		FROM brain.rawframes
		WHERE ts > now64(3) - INTERVAL 1 SECOND
	`);

	return Number(res.data[0]?.count || 0);
}

export async function getDevices(): Promise<DeviceRecord[]> {
	const result = await query(`
		SELECT id, location, sensors, status, updated_at
		FROM brain.devices
		ORDER BY updated_at DESC
	`);

	return result.data.map((row: Record<string, unknown>) => ({
		id: String(row.id ?? ''),
		location: String(row.location ?? ''),
		sensors: String(row.sensors ?? ''),
		status: String(row.status ?? ''),
		updatedAt: String(row.updated_at ?? '')
	}));
}

export async function upsertDevice(input: {
	id: string;
	location: string;
	sensors: string;
	status: string;
}): Promise<void> {
	const id = input.id.trim();
	const location = input.location.trim();
	const sensors = input.sensors.trim();
	const status = input.status.trim() || 'Online';

	if (!id || !location) {
		throw new Error('ID and Location are required');
	}

	await exec(`
		INSERT INTO brain.devices (id, location, sensors, status, updated_at)
		VALUES ('${escapeSql(id)}', '${escapeSql(location)}', '${escapeSql(sensors)}', '${escapeSql(status)}', now())
	`);
}

export async function removeDevice(id: string): Promise<void> {
	const trimmed = id.trim();
	if (!trimmed) {
		throw new Error('ID required');
	}

	await exec(`ALTER TABLE brain.devices DELETE WHERE id = '${escapeSql(trimmed)}'`);
}

export async function getSettings(): Promise<SettingsRecord> {
	const ttlRes = await query(`
		SELECT create_table_query
		FROM system.tables
		WHERE database = 'brain' AND name = 'rawframes'
	`);

	let currentTtlDays = 30;
	const createTableQuery = String(ttlRes.data?.[0]?.create_table_query || '');
	const match =
		createTableQuery.match(/TTL\s+.+?\+\s+INTERVAL\s+(\d+)\s+DAY/i) ||
		createTableQuery.match(/TTL\s+.+?\+\s+toIntervalDay\((\d+)\)/i);
	if (match) {
		currentTtlDays = Number.parseInt(match[1], 10);
	}

	return {
		currentTtlDays,
		s3: {
			endpoint: 'http://minio:9000/brain/clickhouse/',
			region: '',
			access_key: 'minio',
			secret_key: '********'
		}
	};
}

export async function updateTtl(days: number): Promise<void> {
	if (!Number.isFinite(days) || days < 1) {
		throw new Error('Invalid number of days');
	}

	for (const [table, column] of Object.entries(TTL_COLUMNS)) {
		await exec(
			`ALTER TABLE brain.${table} MODIFY TTL ${column} + INTERVAL ${days} DAY TO VOLUME 's3_tiered'`
		);
	}
}

export async function updateS3Config(input: {
	endpoint: string;
	region?: string;
	accessKey: string;
	secretKey: string;
}): Promise<void> {
	const endpoint = input.endpoint.trim();
	const region = (input.region ?? '').trim();
	const accessKey = input.accessKey.trim();
	const secretKey = input.secretKey.trim();

	if (!endpoint || !accessKey || !secretKey) {
		throw new Error('Missing required S3 configuration fields');
	}

	const xml = `<clickhouse>
    <storage_configuration>
        <disks>
            <s3>
                <type>s3</type>
                <endpoint>${endpoint}</endpoint>
                <access_key_id>${accessKey}</access_key_id>
                <secret_access_key>${secretKey}</secret_access_key>
                <region>${region}</region>
                <metadata_path>/var/lib/clickhouse/disks/s3/</metadata_path>
            </s3>
        </disks>
        <policies>
            <tiered>
                <volumes>
                    <default>
                        <disk>default</disk>
                    </default>
                    <s3_tiered>
                        <disk>s3</disk>
                    </s3_tiered>
                </volumes>
                <move_factor>0.2</move_factor>
            </tiered>
        </policies>
    </storage_configuration>
</clickhouse>`;

	const configPath = path.resolve('deploy/clickhouse/config/storage.xml');
	fs.writeFileSync(configPath, xml);
	await exec('SYSTEM RELOAD CONFIG');
}

type HistoryParams = {
	topic?: string | null;
	search?: string | null;
	limit?: string | null;
};

function historySearchClause(columns: string[], search: string): string {
	const escaped = escapeSql(search);
	return columns
		.map((column) => `positionCaseInsensitive(toString(${column}), '${escaped}') > 0`)
		.join(' OR ');
}

function buildWhere(columns: string[], search: string): string {
	if (!search) return '';
	return `WHERE ${historySearchClause(columns, search)}`;
}

export async function getHistoryMessages(params: HistoryParams): Promise<InspectorMessage[]> {
	const topic = (params.topic ?? 'all').trim();
	const search = (params.search ?? '').trim();
	const limit = clampLimit(params.limit, 50);
	const perTableLimit = Math.max(limit, 30);
	const selectedTopics =
		topic === 'all'
			? ['rawframes', 'session-features', 'inference-results', 'inference-alerts', 'env-features', 'power-features']
			: [topic];

	const tasks: Promise<InspectorMessage[]>[] = [];

	if (selectedTopics.includes('rawframes')) tasks.push(loadRawframesHistory(perTableLimit, search));
	if (selectedTopics.includes('session-features')) tasks.push(loadSessionFeatureHistory(perTableLimit, search));
	if (selectedTopics.includes('inference-results')) tasks.push(loadInferenceResultHistory(perTableLimit, search));
	if (selectedTopics.includes('inference-alerts')) tasks.push(loadInferenceAlertHistory(perTableLimit, search));
	if (selectedTopics.includes('env-features')) tasks.push(loadEnvFeatureHistory(perTableLimit, search));
	if (selectedTopics.includes('power-features')) tasks.push(loadPowerFeatureHistory(perTableLimit, search));

	const results = await Promise.allSettled(tasks);
	return results
		.flatMap((result) => (result.status === 'fulfilled' ? result.value : []))
		.sort((a, b) => new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime())
		.slice(0, limit);
}

async function loadRawframesHistory(limit: number, search: string): Promise<InspectorMessage[]> {
	const result = await query(`
		SELECT
			event_id,
			ts,
			source,
			payload,
			if(isValidJSON(payload), JSONExtractString(payload, 'device_id'), '') AS device_id,
			if(isValidJSON(payload), JSONExtractString(payload, 'event_type'), '') AS event_type
		FROM brain.rawframes
		${buildWhere(['event_id', 'payload'], search)}
		ORDER BY ts DESC
		LIMIT ${limit}
	`);

	return result.data.map((row: Record<string, unknown>) => {
		const payloadText = String(row.payload ?? '');
		const sizeBytes = Buffer.byteLength(payloadText, 'utf8');
		return {
			id: String(row.event_id ?? ''),
			topic: 'rawframes',
			eventType: String(row.event_type || 'RawFrame'),
			deviceId: String(row.device_id || ''),
			timestamp: String(row.ts ?? ''),
			sizeBytes,
			size: formatBytes(sizeBytes),
			summary: `source=${String(row.source ?? 'ingest')}`,
			payload: safeJsonParse(payloadText),
			source: 'clickhouse'
		};
	});
}

async function loadSessionFeatureHistory(limit: number, search: string): Promise<InspectorMessage[]> {
	const result = await query(`
		SELECT event_id, device_id, session_id, sensor_type, start_ts_ms, end_ts_ms, count, inserted_at
		FROM brain.session_features
		${buildWhere(['event_id', 'device_id', 'session_id', 'sensor_type'], search)}
		ORDER BY inserted_at DESC
		LIMIT ${limit}
	`);

	return result.data.map((row: Record<string, unknown>) =>
		makeStructuredMessage('session-features', 'SessionFeature', String(row.inserted_at ?? ''), row, [
			'device_id',
			'session_id',
			'sensor_type',
			'start_ts_ms',
			'end_ts_ms',
			'count'
		])
	);
}

async function loadInferenceResultHistory(limit: number, search: string): Promise<InspectorMessage[]> {
	const result = await query(`
		SELECT event_id, device_id, session_id, sensor_type, model_version, label, score, start_ts_ms, end_ts_ms, created_at
		FROM brain.inference_results
		${buildWhere(['event_id', 'device_id', 'session_id', 'label', 'model_version'], search)}
		ORDER BY created_at DESC
		LIMIT ${limit}
	`);

	return result.data.map((row: Record<string, unknown>) =>
		makeStructuredMessage('inference-results', 'InferenceResult', String(row.created_at ?? ''), row, [
			'device_id',
			'session_id',
			'sensor_type',
			'model_version',
			'label',
			'score',
			'start_ts_ms',
			'end_ts_ms'
		])
	);
}

async function loadInferenceAlertHistory(limit: number, search: string): Promise<InspectorMessage[]> {
	const result = await query(`
		SELECT
			hex(sipHash64(concat(device_id, session_id, sensor_type, model_version, label, toString(created_at)))) AS event_id,
			device_id,
			session_id,
			sensor_type,
			model_version,
			label,
			score,
			threshold,
			start_ts_ms,
			end_ts_ms,
			created_at
		FROM brain.inference_alerts
		${buildWhere(['device_id', 'session_id', 'label', 'model_version'], search)}
		ORDER BY created_at DESC
		LIMIT ${limit}
	`);

	return result.data.map((row: Record<string, unknown>) =>
		makeStructuredMessage('inference-alerts', 'InferenceAlert', String(row.created_at ?? ''), row, [
			'device_id',
			'session_id',
			'sensor_type',
			'model_version',
			'label',
			'score',
			'threshold',
			'start_ts_ms',
			'end_ts_ms'
		])
	);
}

async function loadEnvFeatureHistory(limit: number, search: string): Promise<InspectorMessage[]> {
	const result = await query(`
		SELECT
			hex(sipHash64(concat(device_id, sensor_type, toString(window_start_ms), toString(window_end_ms), toString(inserted_at)))) AS event_id,
			device_id,
			sensor_type,
			window_start_ms,
			window_end_ms,
			count,
			mean_value,
			inserted_at
		FROM brain.env_features
		${buildWhere(['device_id', 'sensor_type'], search)}
		ORDER BY inserted_at DESC
		LIMIT ${limit}
	`);

	return result.data.map((row: Record<string, unknown>) =>
		makeStructuredMessage('env-features', 'EnvFeature', String(row.inserted_at ?? ''), row, [
			'device_id',
			'sensor_type',
			'window_start_ms',
			'window_end_ms',
			'count',
			'mean_value'
		])
	);
}

async function loadPowerFeatureHistory(limit: number, search: string): Promise<InspectorMessage[]> {
	const result = await query(`
		SELECT
			hex(sipHash64(concat(device_id, sensor_type, toString(window_start_ms), toString(window_end_ms), toString(inserted_at)))) AS event_id,
			device_id,
			sensor_type,
			window_start_ms,
			window_end_ms,
			count,
			mean_value,
			inserted_at
		FROM brain.power_features
		${buildWhere(['device_id', 'sensor_type'], search)}
		ORDER BY inserted_at DESC
		LIMIT ${limit}
	`);

	return result.data.map((row: Record<string, unknown>) =>
		makeStructuredMessage('power-features', 'PowerFeature', String(row.inserted_at ?? ''), row, [
			'device_id',
			'sensor_type',
			'window_start_ms',
			'window_end_ms',
			'count',
			'mean_value'
		])
	);
}

function makeStructuredMessage(
	topic: string,
	eventType: string,
	timestamp: string,
	row: Record<string, unknown>,
	keys: string[]
): InspectorMessage {
	const payload = Object.fromEntries(keys.map((key) => [key, row[key] ?? null]));
	const payloadText = JSON.stringify(payload);
	const sizeBytes = Buffer.byteLength(payloadText, 'utf8');

	return {
		id: String(row.event_id ?? ''),
		topic,
		eventType,
		deviceId: String(row.device_id ?? ''),
		timestamp,
		sizeBytes,
		size: formatBytes(sizeBytes),
		summary: `${eventType} from ClickHouse`,
		payload,
		source: 'clickhouse'
	};
}

function safeJsonParse(value: string): unknown {
	try {
		return JSON.parse(value);
	} catch {
		return value;
	}
}

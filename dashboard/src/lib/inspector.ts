export type InspectorSource = 'kafka' | 'clickhouse';

export type InspectorMessage = {
	id: string;
	topic: string;
	eventType: string;
	deviceId: string;
	timestamp: string;
	sizeBytes: number;
	size: string;
	summary: string;
	payload: unknown;
	source: InspectorSource;
};

export const LIVE_TOPICS = [
	'all',
	'rawframes',
	'rawframes-processed',
	'session-features',
	'inference-results',
	'inference-alerts',
	'env-features',
	'power-features'
] as const;

export const HISTORY_TOPICS = [
	'all',
	'rawframes',
	'session-features',
	'inference-results',
	'inference-alerts',
	'env-features',
	'power-features'
] as const;

export type LiveTopic = (typeof LIVE_TOPICS)[number];
export type HistoryTopic = (typeof HISTORY_TOPICS)[number];

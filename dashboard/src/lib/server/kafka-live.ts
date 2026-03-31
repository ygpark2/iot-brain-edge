import { Kafka, logLevel, type Consumer } from 'kafkajs';

import { LIVE_TOPICS, type InspectorMessage } from '$lib/inspector';

import { formatBytes } from './dashboard-api';

type LiveSnapshot = {
	status: 'idle' | 'connecting' | 'connected' | 'error';
	error: string | null;
	messages: InspectorMessage[];
	brokers: string[];
};

const DEFAULT_TOPICS = LIVE_TOPICS.filter((topic) => topic !== 'all');
const MAX_BUFFER = 250;
const KAFKA_LIVE_VERSION = 3;

function candidateKafkaBrokers(): string[] {
	const configured = process.env.DASHBOARD_KAFKA_BROKERS
		?.split(',')
		.map((value) => value.trim())
		.filter(Boolean);

	if (configured && configured.length > 0) {
		return [...new Set(configured)];
	}

	const defaults =
		process.env.DOCKER_ENV === 'true'
			? ['kafka:9092', 'host.docker.internal:9094', 'localhost:9094']
			: ['localhost:9094', 'host.docker.internal:9094'];

	return [...new Set(defaults)];
}

function defaultEventType(topic: string): string {
	switch (topic) {
		case 'rawframes':
		case 'rawframes-processed':
			return 'RawFrame';
		case 'session-features':
			return 'SessionFeature';
		case 'inference-results':
			return 'InferenceResult';
		case 'inference-alerts':
			return 'InferenceAlert';
		case 'env-features':
			return 'EnvFeature';
		case 'power-features':
			return 'PowerFeature';
		default:
			return 'EventEnvelope';
	}
}

class KafkaLiveBuffer {
	private readonly brokers = candidateKafkaBrokers();
	private readonly messages: InspectorMessage[] = [];
	private readonly topics: string[] = [];
	private status: LiveSnapshot['status'] = 'idle';
	private error: string | null = null;
	private startPromise: Promise<void> | null = null;
	private started = false;
	private consumer: Consumer | null = null;

	async ensureStarted(): Promise<void> {
		if (this.started && this.topics.length > 0) return;
		if (this.started && this.topics.length === 0) {
			this.started = false;
		}
		if (this.startPromise) return this.startPromise;

		this.startPromise = this.start().finally(() => {
			this.startPromise = null;
		});

		return this.startPromise;
	}

	snapshot(params: { topic?: string | null; search?: string | null; limit?: string | null }): LiveSnapshot {
		const topic = (params.topic ?? 'all').trim();
		const search = (params.search ?? '').trim().toLowerCase();
		const limit = Number.parseInt(params.limit ?? '20', 10);
		const safeLimit = Number.isNaN(limit) ? 20 : Math.max(1, Math.min(limit, 100));

		const messages = this.messages
			.filter((message) => (topic === 'all' ? true : message.topic === topic))
			.filter((message) => {
				if (!search) return true;
				const haystack = [
					message.id,
					message.topic,
					message.eventType,
					message.deviceId,
					message.summary,
					typeof message.payload === 'string' ? message.payload : JSON.stringify(message.payload)
				]
					.join(' ')
					.toLowerCase();
				return haystack.includes(search);
			})
			.slice(0, safeLimit);

		return {
			status: this.status,
			error: this.error,
			messages,
			brokers: this.brokers
		};
	}

	private async start(): Promise<void> {
		this.status = 'connecting';
		this.error = null;

		try {
			await withTimeout((async () => {
				const kafka = new Kafka({
					clientId: `dashboard-live-${process.pid}`,
					brokers: this.brokers,
					logLevel: logLevel.NOTHING,
					connectionTimeout: 3000,
					requestTimeout: 3000,
					retry: { retries: 2 }
				});

				const consumer = kafka.consumer({
					groupId: `dashboard-live-${process.pid}-${Math.random().toString(16).slice(2)}`
				});
				const admin = kafka.admin();
				this.consumer = consumer;

				consumer.on(consumer.events.CRASH, (event) => {
					this.status = 'error';
					this.error = String(event.payload.error ?? 'Kafka consumer crashed');
					this.started = false;
				});

				consumer.on(consumer.events.DISCONNECT, () => {
					if (this.status !== 'error') this.status = 'connecting';
				});

				await admin.connect();
				const existingTopics = await admin.listTopics();
				await admin.disconnect();

				const availableTopics = DEFAULT_TOPICS.filter((topic) => existingTopics.includes(topic));
				this.topics.splice(0, this.topics.length, ...availableTopics);

				this.status = 'connected';
				this.error = null;
				this.started = true;

				if (availableTopics.length === 0) {
					return;
				}

				await consumer.connect();
				for (const topic of availableTopics) {
					await consumer.subscribe({ topic, fromBeginning: false });
				}

				void consumer
					.run({
						eachMessage: async ({ topic, partition, message }) => {
							const value = message.value?.toString('utf8') ?? '';
							const payload = parsePayload(value);
							const parsed =
								typeof payload === 'object' && payload ? (payload as Record<string, unknown>) : {};
							const eventType =
								typeof parsed.event_type === 'string' ? parsed.event_type : defaultEventType(topic);
							const deviceId =
								typeof parsed.device_id === 'string'
									? parsed.device_id
									: typeof parsed.deviceId === 'string'
										? parsed.deviceId
										: '';
							const sizeBytes = message.value?.length ?? 0;

							this.messages.unshift({
								id:
									typeof parsed.event_id === 'string'
										? parsed.event_id
										: message.key?.toString('utf8') || `${topic}-${partition}-${message.offset}`,
								topic,
								eventType,
								deviceId,
								timestamp: new Date().toISOString(),
								sizeBytes,
								size: formatBytes(sizeBytes),
								summary: `partition=${partition} offset=${message.offset}`,
								payload,
								source: 'kafka'
							});

							if (this.messages.length > MAX_BUFFER) {
								this.messages.length = MAX_BUFFER;
							}
						}
					})
					.catch((error) => {
						this.status = 'error';
						this.error = error instanceof Error ? error.message : String(error);
						this.started = false;
						this.consumer = null;
					});

			})(), 5000, 'Kafka bootstrap timeout');
		} catch (error) {
			this.status = 'error';
			this.error = error instanceof Error ? error.message : String(error);
			this.started = false;
			this.consumer = null;
			throw error;
		}
	}
}

function parsePayload(value: string): unknown {
	try {
		return JSON.parse(value);
	} catch {
		return value;
	}
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, message: string): Promise<T> {
	let timeoutHandle: ReturnType<typeof setTimeout> | undefined;
	const timeoutPromise = new Promise<never>((_, reject) => {
		timeoutHandle = setTimeout(() => reject(new Error(message)), timeoutMs);
	});

	return Promise.race([promise, timeoutPromise]).finally(() => {
		if (timeoutHandle) clearTimeout(timeoutHandle);
	});
}

const globalState = globalThis as typeof globalThis & {
	__brainKafkaLive__?: KafkaLiveBuffer;
	__brainKafkaLiveVersion__?: number;
};

export function getKafkaLiveBuffer(): KafkaLiveBuffer {
	if (
		!globalState.__brainKafkaLive__ ||
		globalState.__brainKafkaLiveVersion__ !== KAFKA_LIVE_VERSION
	) {
		globalState.__brainKafkaLive__ = new KafkaLiveBuffer();
		globalState.__brainKafkaLiveVersion__ = KAFKA_LIVE_VERSION;
	}
	return globalState.__brainKafkaLive__;
}

function candidateBaseUrls() {
	const configured = process.env.CLICKHOUSE_HTTP_URLS
		?.split(',')
		.map((value) => value.trim())
		.filter(Boolean);

	if (configured && configured.length > 0) {
		return [...new Set(configured)];
	}

	const defaults =
		process.env.DOCKER_ENV === 'true'
			? [
					'http://host.docker.internal:8123',
					'http://clickhouse:8123',
					'http://localhost:8123'
				]
			: ['http://localhost:8123', 'http://host.docker.internal:8123'];

	return [...new Set(defaults)];
}

export async function query(sql: string) {
	const baseUrls = candidateBaseUrls();
	let lastError: unknown;

	for (const baseUrl of baseUrls) {
		try {
			const response = await fetch(baseUrl, {
				method: 'POST',
				body: sql + ' FORMAT JSON',
				headers: {
					'Content-Type': 'application/plain',
					'X-ClickHouse-User': 'brain',
					'X-ClickHouse-Key': 'brain'
				},
				signal: AbortSignal.timeout(2000) // 2초 타임아웃
			});

			if (response.ok) {
				return response.json();
			}

			const errorText = await response.text();
			lastError = new Error(`ClickHouse ${response.status} from ${baseUrl}: ${errorText}`);
		} catch (e) {
			lastError = e;
			continue;
		}
	}
	
	throw new Error(`ClickHouse connection failed on all hosts (${baseUrls.join(', ')}). Last error: ${lastError}`);
}

export async function exec(sql: string) {
	const baseUrls = candidateBaseUrls();
	let lastError: unknown;

	for (const baseUrl of baseUrls) {
		try {
			const response = await fetch(baseUrl, {
				method: 'POST',
				body: sql,
				headers: {
					'Content-Type': 'application/plain',
					'X-ClickHouse-User': 'brain',
					'X-ClickHouse-Key': 'brain'
				},
				signal: AbortSignal.timeout(2000)
			});

			if (response.ok) {
				return response.text();
			}

			const errorText = await response.text();
			lastError = new Error(`ClickHouse ${response.status} from ${baseUrl}: ${errorText}`);
		} catch (e) {
			lastError = e;
			continue;
		}
	}

	throw new Error(`ClickHouse connection failed on all hosts (${baseUrls.join(', ')}). Last error: ${lastError}`);
}

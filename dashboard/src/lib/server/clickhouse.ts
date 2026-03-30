export async function query(sql: string) {
	const response = await fetch('http://localhost:8123', {
		method: 'POST',
		body: sql + ' FORMAT JSON',
		headers: {
			'Content-Type': 'application/plain',
			'X-ClickHouse-User': 'brain',
			'X-ClickHouse-Key': 'brain'
		}
	});

	if (!response.ok) {
		const error = await response.text();
		throw new Error(`ClickHouse error: ${error}`);
	}

	return response.json();
}

export async function exec(sql: string) {
	const response = await fetch('http://localhost:8123', {
		method: 'POST',
		body: sql,
		headers: {
			'Content-Type': 'application/plain',
			'X-ClickHouse-User': 'brain',
			'X-ClickHouse-Key': 'brain'
		}
	});

	if (!response.ok) {
		const error = await response.text();
		throw new Error(`ClickHouse error: ${error}`);
	}

	return response.text();
}

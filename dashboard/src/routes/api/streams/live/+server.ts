import { json } from '@sveltejs/kit';

import { getKafkaLiveBuffer } from '$lib/server/kafka-live';

export async function GET({ url }) {
	const buffer = getKafkaLiveBuffer();

	await buffer.ensureStarted().catch(() => {
		// Snapshot includes current error state.
	});

	return json(
		buffer.snapshot({
			topic: url.searchParams.get('topic'),
			search: url.searchParams.get('search'),
			limit: url.searchParams.get('limit')
		})
	);
}

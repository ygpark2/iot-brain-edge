import { json } from '@sveltejs/kit';

import { getSettings } from '$lib/server/dashboard-api';

export async function GET() {
	try {
		return json(await getSettings());
	} catch (error) {
		return json({ error: String(error), currentTtlDays: 30, s3: {} }, { status: 500 });
	}
}

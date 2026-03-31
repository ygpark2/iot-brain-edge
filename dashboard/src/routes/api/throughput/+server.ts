import { json } from '@sveltejs/kit';
import { getThroughputCount } from '$lib/server/dashboard-api';
import type { RequestHandler } from './$types';

export const GET: RequestHandler = async () => {
	try {
		return json({ count: await getThroughputCount() });
	} catch (e) {
		return json({ count: 0, error: String(e) }, { status: 500 });
	}
};

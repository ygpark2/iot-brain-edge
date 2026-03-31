import { json } from '@sveltejs/kit';

import { getDashboardMetrics } from '$lib/server/dashboard-api';

export async function GET() {
	try {
		return json(await getDashboardMetrics());
	} catch (error) {
		return json({ error: String(error) }, { status: 500 });
	}
}

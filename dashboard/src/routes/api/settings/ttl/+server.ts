import { json } from '@sveltejs/kit';

import { updateTtl } from '$lib/server/dashboard-api';

export async function POST({ request }) {
	try {
		const body = await request.json();
		await updateTtl(Number(body.ttlDays));
		return json({
			success: true,
			message: `Successfully updated TTL to ${body.ttlDays} days for all tables.`
		});
	} catch (error) {
		return json(
			{ success: false, message: error instanceof Error ? error.message : String(error) },
			{ status: 400 }
		);
	}
}

import { json } from '@sveltejs/kit';

import { updateS3Config } from '$lib/server/dashboard-api';

export async function POST({ request }) {
	try {
		const body = await request.json();
		await updateS3Config({
			endpoint: String(body.endpoint ?? ''),
			region: String(body.region ?? ''),
			accessKey: String(body.accessKey ?? ''),
			secretKey: String(body.secretKey ?? '')
		});
		return json({
			success: true,
			message: 'Successfully updated S3 configuration and reloaded ClickHouse.'
		});
	} catch (error) {
		return json(
			{ success: false, message: error instanceof Error ? error.message : String(error) },
			{ status: 400 }
		);
	}
}

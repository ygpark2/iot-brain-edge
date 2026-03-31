import { json } from '@sveltejs/kit';

import { getDevices, removeDevice, upsertDevice } from '$lib/server/dashboard-api';

export async function GET() {
	try {
		return json({ devices: await getDevices() });
	} catch (error) {
		return json({ devices: [], error: String(error) }, { status: 500 });
	}
}

export async function POST({ request }) {
	try {
		const body = await request.json();
		await upsertDevice({
			id: String(body.id ?? ''),
			location: String(body.location ?? ''),
			sensors: String(body.sensors ?? ''),
			status: String(body.status ?? 'Online')
		});
		return json({ success: true });
	} catch (error) {
		return json(
			{ success: false, error: error instanceof Error ? error.message : String(error) },
			{ status: 400 }
		);
	}
}

export async function DELETE({ request }) {
	try {
		const body = await request.json();
		await removeDevice(String(body.id ?? ''));
		return json({ success: true });
	} catch (error) {
		return json(
			{ success: false, error: error instanceof Error ? error.message : String(error) },
			{ status: 400 }
		);
	}
}

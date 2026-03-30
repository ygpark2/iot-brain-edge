import * as ch from '$lib/server/clickhouse';
import { fail } from '@sveltejs/kit';

export async function load() {
	try {
		// devices 메타데이터 테이블에서 목록 조회
		const result = await ch.query(`
			SELECT id, location, sensors, status, updated_at 
			FROM brain.devices 
			ORDER BY updated_at DESC
		`);

		return {
			devices: result.data.map((r: any) => ({
				id: r.id,
				location: r.location,
				sensors: r.sensors,
				status: r.status,
				updatedAt: r.updated_at
			}))
		};
	} catch (e) {
		console.error('Failed to load devices:', e);
		return { devices: [] };
	}
}

export const actions = {
	upsert: async ({ request }) => {
		const data = await request.formData();
		const id = data.get('id');
		const location = data.get('location');
		const sensors = data.get('sensors');
		const status = data.get('status');

		if (!id || !location) {
			return fail(400, { error: 'ID and Location are required' });
		}

		try {
			// ReplacingMergeTree를 활용한 Insert/Update (Upsert)
			await ch.exec(`
				INSERT INTO brain.devices (id, location, sensors, status, updated_at) 
				VALUES ('${id}', '${location}', '${sensors}', '${status}', now())
			`);
			return { success: true };
		} catch (e) {
			console.error('Failed to upsert device:', e);
			return fail(500, { error: 'Database error' });
		}
	},

	delete: async ({ request }) => {
		const data = await request.formData();
		const id = data.get('id');

		if (!id) return fail(400, { error: 'ID required' });

		try {
			// ClickHouse Mutation을 통한 삭제
			await ch.exec(`ALTER TABLE brain.devices DELETE WHERE id = '${id}'`);
			return { success: true };
		} catch (e) {
			console.error('Failed to delete device:', e);
			return fail(500, { error: 'Database error' });
		}
	}
};
